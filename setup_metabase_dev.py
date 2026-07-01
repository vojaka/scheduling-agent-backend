import os
import requests
import sys

def main():
    print("====================================================")
    print("       ComfortHub Metabase Dev Automatic Setup       ")
    print("====================================================")
    
    metabase_url = "http://localhost:3000"
    admin_email = "kim.smirnov@gmail.com"
    admin_password = "ComfortHubPass2026!"
    db_password = os.environ.get("POSTGRES_PASSWORD", "comfort-hub-db-pass-2026")

    # 1. Fetch setup token
    print("Checking Metabase status and fetching setup token...")
    try:
        r = requests.get(f"{metabase_url}/api/session/properties", timeout=10)
        r.raise_for_status()
        props = r.json()
        setup_token = props.get("setup-token")
        has_user_setup = props.get("has-user-setup")
    except Exception as e:
        print(f"FAILED to check Metabase status: {e}")
        sys.exit(1)

    session_id = None
    if has_user_setup:
        print("Metabase already has a user setup. Logging in...")
        try:
            r = requests.post(f"{metabase_url}/api/session", json={
                "username": admin_email,
                "password": admin_password
            }, timeout=10)
            r.raise_for_status()
            session_id = r.json()["id"]
            print("Successfully logged in.")
        except Exception as e:
            print(f"FAILED to log in. Maybe the admin password is different? {e}")
            sys.exit(1)
    else:
        if not setup_token:
            print("FAILED: No setup token found and no user setup complete.")
            sys.exit(1)
        print(f"Completing setup wizard with token {setup_token}...")
        setup_payload = {
            "token": setup_token,
            "user": {
                "email": admin_email,
                "first_name": "Kim",
                "last_name": "Smirnov",
                "password": admin_password,
                "site_name": "ComfortHub Dev"
            },
            "prefs": {
                "site_name": "ComfortHub Dev",
                "allow_tracking": False
            }
        }
        try:
            r = requests.post(f"{metabase_url}/api/setup", json=setup_payload, timeout=15)
            r.raise_for_status()
            session_id = r.json().get("id")
            print("Successfully completed setup. Logged in.")
        except Exception as e:
            print(f"FAILED to complete setup: {e}")
            sys.exit(1)

    headers = {
        "X-Metabase-Session": session_id,
        "Content-Type": "application/json"
    }

    # 2. Enable embedding settings in Metabase
    print("Enabling embedding in Metabase settings...")
    for setting in ["enable-embedding", "enable-embedding-static"]:
        try:
            r = requests.put(f"{metabase_url}/api/setting/{setting}", headers=headers, json={"value": True}, timeout=10)
            r.raise_for_status()
            print(f"- {setting} successfully enabled.")
        except Exception as e:
            print(f"- FAILED to enable {setting}: {e}")

    # 3. Connect to postgres_dev database
    print("Connecting to postgres_dev database...")
    db_id = None
    try:
        r = requests.get(f"{metabase_url}/api/database", headers=headers, timeout=10)
        r.raise_for_status()
        databases = r.json().get("data", [])
        for db in databases:
            if db.get("details", {}).get("dbname") == "postgres_dev":
                db_id = db["id"]
                print(f"postgres_dev database already connected (ID: {db_id})")
                break
        
        if not db_id:
            db_payload = {
                "name": "ComfortHub Dev DB",
                "engine": "postgres",
                "details": {
                    "host": "db",
                    "port": 5432,
                    "dbname": "postgres_dev",
                    "user": "postgres",
                    "password": db_password,
                    "ssl": False
                }
            }
            r = requests.post(f"{metabase_url}/api/database", headers=headers, json=db_payload, timeout=15)
            r.raise_for_status()
            db_id = r.json()["id"]
            print(f"Successfully connected postgres_dev database (ID: {db_id})")
    except Exception as e:
        print(f"FAILED to connect database: {e}")
        sys.exit(1)

    # 4. Create reports (questions)
    print("Creating reports...")
    questions = [
        {
            "name": "Total Hours Scheduled per Worker",
            "description": "Total scheduled hours per worker for the current week. #backoffice",
            "dataset_query": {
                "database": db_id,
                "type": "native",
                "native": {
                    "query": """SELECT 
    u.full_name AS worker_name,
    ROUND(SUM(EXTRACT(EPOCH FROM (s.end_time - s.start_time))/3600)::numeric, 2) AS total_hours
FROM shifts s
JOIN users u ON s.assigned_user = u.bubble_id
GROUP BY u.full_name
ORDER BY total_hours DESC;"""
                }
            },
            "display": "row",
            "visualization_settings": {
                "graph.show_values": True
            }
        },
        {
            "name": "Shift Distribution by Store",
            "description": "Shift distribution across stores. #backoffice",
            "dataset_query": {
                "database": db_id,
                "type": "native",
                "native": {
                    "query": """SELECT 
    COALESCE(st.name, s.assigned_store, 'Unassigned') AS store_name,
    COUNT(s.id) AS total_shifts
FROM shifts s
LEFT JOIN stores st ON s.assigned_store = st.bubble_id
GROUP BY COALESCE(st.name, s.assigned_store, 'Unassigned')
ORDER BY total_shifts DESC;"""
                }
            },
            "display": "pie",
            "visualization_settings": {}
        },
        {
            "name": "Estimated Payroll Cost per Worker",
            "description": "Estimated payroll cost per worker based on wage rates. #backoffice",
            "dataset_query": {
                "database": db_id,
                "type": "native",
                "native": {
                    "query": """SELECT 
    u.full_name AS worker_name,
    COUNT(s.id) AS total_shifts,
    ROUND(SUM(EXTRACT(EPOCH FROM (s.end_time - s.start_time))/3600)::numeric, 2) AS total_hours,
    ROUND(SUM((EXTRACT(EPOCH FROM (s.end_time - s.start_time))/3600) * u.wage_rate)::numeric, 2) AS est_cost_eur
FROM shifts s
JOIN users u ON s.assigned_user = u.bubble_id
GROUP BY u.full_name
ORDER BY est_cost_eur DESC;"""
                }
            },
            "display": "table",
            "visualization_settings": {}
        }
    ]

    card_ids = []
    for q in questions:
        try:
            r = requests.post(f"{metabase_url}/api/card", headers=headers, json=q, timeout=10)
            r.raise_for_status()
            card = r.json()
            card_id = card["id"]
            card_ids.append(card_id)
            print(f"- Created report: '{q['name']}' (ID: {card_id})")

            # Enable embedding on this card
            requests.put(f"{metabase_url}/api/card/{card_id}", headers=headers, json={
                "enable_embedding": True
            }, timeout=10).raise_for_status()
            print(f"- Enabled embedding on card: '{q['name']}'")
        except Exception as e:
            print(f"FAILED to create report or enable embedding '{q['name']}': {e}")
            sys.exit(1)

    # 5. Create Dashboard
    print("Creating dashboard...")
    dash_id = None
    try:
        r = requests.post(f"{metabase_url}/api/dashboard", headers=headers, json={
            "name": "Workforce Scheduling Analytics",
            "description": "Overview of scheduled hours, shift counts, and estimated payroll costs. #backoffice"
        }, timeout=10)
        r.raise_for_status()
        dash_id = r.json()["id"]
        print(f"Dashboard created successfully (ID: {dash_id})")
    except Exception as e:
        print(f"FAILED to create dashboard: {e}")
        sys.exit(1)

    # 6. Enable embedding on this dashboard
    print("Enabling embedding on the dashboard...")
    try:
        r = requests.put(f"{metabase_url}/api/dashboard/{dash_id}", headers=headers, json={
            "enable_embedding": True
        }, timeout=10)
        r.raise_for_status()
        print("Dashboard embedding enabled.")
    except Exception as e:
        print(f"FAILED to enable dashboard embedding: {e}")

    # 7. Add cards and configure layout
    print("Configuring dashboard layout...")
    layout = [
        {"size_x": 12, "size_y": 6, "col": 0, "row": 0},   # Total Hours
        {"size_x": 6, "size_y": 6, "col": 0, "row": 6},    # Shift Distribution
        {"size_x": 6, "size_y": 6, "col": 6, "row": 6}     # Payroll Cost
    ]
    cards_payload = []
    for i, cid in enumerate(card_ids):
        lay = layout[i]
        cards_payload.append({
            "id": -(i + 1),
            "card_id": cid,
            "size_x": lay["size_x"],
            "size_y": lay["size_y"],
            "col": lay["col"],
            "row": lay["row"]
        })
    try:
        r = requests.put(f"{metabase_url}/api/dashboard/{dash_id}/cards", headers=headers, json={
            "cards": cards_payload
        }, timeout=10)
        r.raise_for_status()
        print("Dashboard cards and layout configured.")
    except Exception as e:
        print(f"FAILED to configure dashboard layout: {e}")
        sys.exit(1)

    print("\n====================================================")
    print("SUCCESS: Your Workforce Scheduling Dashboard is ready!")
    print(f"Open: http://178.105.76.235:3000/dashboard/{dash_id}")
    print("====================================================")

if __name__ == "__main__":
    main()
