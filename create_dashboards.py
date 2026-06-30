import os
import requests
import sys
import getpass

def main():
    print("====================================================")
    print("       ComfortHub Metabase Dashboard Creator        ")
    print("====================================================")
    print("This script connects to your Metabase server and")
    print("creates your Workforce Analytics dashboard automatically.")
    print("----------------------------------------------------\n")

    metabase_url = input("Metabase URL [http://localhost:3000]: ").strip()
    if not metabase_url:
        metabase_url = "http://localhost:3000"
    if metabase_url.endswith("/"):
        metabase_url = metabase_url[:-1]

    email = input("Metabase Admin Email: ")
    password = getpass.getpass("Metabase Password: ")

    # 1. Log in to get session token
    print("\nLogging in to Metabase...")
    try:
        r = requests.post(f"{metabase_url}/api/session", json={
            "username": email,
            "password": password
        }, timeout=5)
        r.raise_for_status()
        session_id = r.json()["id"]
        print("Successfully logged in.")
    except Exception as e:
        print(f"FAILED to log in: {e}")
        print("Please check your email, password, and ensure Metabase is running at http://localhost:3000.")
        sys.exit(1)

    headers = {
        "X-Metabase-Session": session_id,
        "Content-Type": "application/json"
    }

    # 2. Get Supabase database ID
    print("Fetching connected databases...")
    db_id = None
    try:
        r = requests.get(f"{metabase_url}/api/database", headers=headers, timeout=5)
        r.raise_for_status()
        databases = r.json().get("data", [])
        
        # Look for the PostgreSQL database representing Supabase or Local DB
        for db in databases:
            if db.get("engine") == "postgres":
                db_id = db["id"]
                print(f"Found database '{db['name']}' with ID: {db_id}")
                break

        if not db_id:
            print("PostgreSQL database not found. Connecting to local PostgreSQL container automatically...")
            db_payload = {
                "name": "bubble",
                "engine": "postgres",
                "details": {
                    "host": "db",
                    "port": 5432,
                    "dbname": "postgres",
                    "user": "postgres",
                    "password": os.environ.get("DB_PASSWORD", "postgres"),
                    "ssl": False
                }
            }
            r = requests.post(f"{metabase_url}/api/database", headers=headers, json=db_payload, timeout=15)
            r.raise_for_status()
            db_id = r.json()["id"]
            print(f"Successfully connected to PostgreSQL container with ID: {db_id}")
            
    except Exception as e:
        print(f"FAILED to fetch/connect database: {e}")
        sys.exit(1)

    # 3. Create the Questions (Cards)
    print("\nCreating reports...")
    
    questions = [
        {
            "name": "Total Hours Scheduled per Worker",
            "dataset_query": {
                "database": db_id,
                "type": "native",
                "native": {
                    "query": """SELECT 
    u.fullname AS worker_name,
    ROUND(SUM(EXTRACT(EPOCH FROM (s.time_end_time - s.time_start_time))/3600)::numeric, 2) AS total_hours
FROM bubble_shift s
JOIN bubble_user u ON s.assigned_user = u.id
GROUP BY u.fullname
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
            "dataset_query": {
                "database": db_id,
                "type": "native",
                "native": {
                    "query": """SELECT 
    COALESCE(st.store_name, s.assigned_store, 'Unassigned') AS store_name,
    COUNT(s.id) AS total_shifts
FROM bubble_shift s
LEFT JOIN bubble_store st ON s.assigned_store = st.id
GROUP BY COALESCE(st.store_name, s.assigned_store, 'Unassigned')
ORDER BY total_shifts DESC;"""
                }
            },
            "display": "pie",
            "visualization_settings": {}
        },
        {
            "name": "Estimated Payroll Cost per Worker",
            "dataset_query": {
                "database": db_id,
                "type": "native",
                "native": {
                    "query": """SELECT 
    u.fullname AS worker_name,
    COUNT(s.id) AS total_shifts,
    ROUND(SUM(EXTRACT(EPOCH FROM (s.time_end_time - s.time_start_time))/3600)::numeric, 2) AS total_hours,
    ROUND(SUM((EXTRACT(EPOCH FROM (s.time_end_time - s.time_start_time))/3600) * w.rate)::numeric, 2) AS est_cost_eur
FROM bubble_shift s
JOIN bubble_user u ON s.assigned_user = u.id
JOIN bubble_wagerate w ON u.id = w.user
GROUP BY u.fullname
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
            r = requests.post(f"{metabase_url}/api/card", headers=headers, json=q, timeout=5)
            r.raise_for_status()
            card = r.json()
            card_ids.append(card["id"])
            print(f"- Created report: '{q['name']}' (ID: {card['id']})")
        except Exception as e:
            print(f"FAILED to create report '{q['name']}': {e}")
            sys.exit(1)

    # 4. Create Dashboard
    print("\nCreating dashboard...")
    dash_id = None
    try:
        r = requests.post(f"{metabase_url}/api/dashboard", headers=headers, json={
            "name": "Workforce Scheduling Analytics",
            "description": "Overview of scheduled hours, shift counts, and estimated payroll costs."
        }, timeout=5)
        r.raise_for_status()
        dash_id = r.json()["id"]
        print(f"Dashboard created successfully with ID: {dash_id}")
    except Exception as e:
        print(f"FAILED to create dashboard: {e}")
        sys.exit(1)

    # 5. Add and Position Cards on Dashboard Layout
    print("Configuring dashboard cards and layout...")
    
    # Grid coordinates: size_x, size_y, col, row
    layout = [
        {"size_x": 12, "size_y": 6, "col": 0, "row": 0},   # Total Hours (Wide Bar chart)
        {"size_x": 6, "size_y": 6, "col": 0, "row": 6},    # Shift Distribution (Pie chart)
        {"size_x": 6, "size_y": 6, "col": 6, "row": 6}     # Payroll Cost (Table)
    ]
    
    cards_payload = []
    for i, cid in enumerate(card_ids):
        lay = layout[i]
        cards_payload.append({
            "id": -(i + 1),  # Negative IDs represent new cards to add in modern Metabase bulk PUT API
            "card_id": cid,
            "size_x": lay["size_x"],
            "size_y": lay["size_y"],
            "col": lay["col"],
            "row": lay["row"]
        })

    try:
        r = requests.put(f"{metabase_url}/api/dashboard/{dash_id}/cards", headers=headers, json={
            "cards": cards_payload
        }, timeout=5)
        r.raise_for_status()
        print("Dashboard cards and layout configured successfully.")
    except Exception as e:
        print(f"FAILED to configure dashboard layout: {e}")
        sys.exit(1)

    print("\n====================================================")
    print("SUCCESS: Your Workforce Scheduling Dashboard is ready!")
    print(f"Open: {metabase_url}/dashboard/{dash_id}")
    print("====================================================")


if __name__ == "__main__":
    main()
