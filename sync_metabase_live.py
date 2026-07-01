import os
import requests
import sys

def get_session(url, email, password):
    # Get properties to check setup state
    try:
        r = requests.get(f"{url}/api/session/properties", timeout=10)
        r.raise_for_status()
        props = r.json()
        has_user_setup = props.get("has-user-setup")
        setup_token = props.get("setup-token")
    except Exception as e:
        print(f"Error checking Metabase properties at {url}: {e}")
        return None

    if not has_user_setup:
        if not setup_token:
            print(f"Metabase at {url} is not set up and no setup token was found.")
            return None
        print(f"Completing setup wizard at {url}...")
        setup_payload = {
            "token": setup_token,
            "user": {
                "email": email,
                "first_name": "Kim",
                "last_name": "Smirnov",
                "password": password,
                "site_name": "ComfortHub Live"
            },
            "prefs": {
                "site_name": "ComfortHub Live",
                "allow_tracking": False
            }
        }
        try:
            r = requests.post(f"{url}/api/setup", json=setup_payload, timeout=15)
            r.raise_for_status()
            return r.json().get("id")
        except Exception as e:
            print(f"Setup failed at {url}: {e}")
            return None
    else:
        # Log in
        try:
            r = requests.post(f"{url}/api/session", json={
                "username": email,
                "password": password
            }, timeout=10)
            r.raise_for_status()
            return r.json()["id"]
        except Exception as e:
            print(f"Login failed at {url}: {e}")
            return None

def ensure_postgres_live(url, headers, db_password):
    print(f"Ensuring postgres_live database is connected to Metabase Live...")
    try:
        r = requests.get(f"{url}/api/database", headers=headers, timeout=10)
        r.raise_for_status()
        databases = r.json().get("data", [])
        for db in databases:
            if db.get("details", {}).get("dbname") == "postgres_live":
                print(f"postgres_live database already connected (ID: {db['id']})")
                return db["id"]
        
        # Connect new database
        print("Connecting postgres_live database...")
        db_payload = {
            "name": "ComfortHub Live DB",
            "engine": "postgres",
            "details": {
                "host": "db",
                "port": 5432,
                "dbname": "postgres_live",
                "user": "postgres",
                "password": db_password,
                "ssl": False
            }
        }
        r = requests.post(f"{url}/api/database", headers=headers, json=db_payload, timeout=15)
        r.raise_for_status()
        db_id = r.json()["id"]
        print(f"Successfully connected postgres_live database (ID: {db_id})")
        return db_id
    except Exception as e:
        print(f"Failed to connect postgres_live database: {e}")
        return None

def main():
    print("====================================================")
    print("       Metabase Dev -> Live Dashboard Sync         ")
    print("====================================================")

    dev_url = "http://localhost:3000"
    live_url = "http://localhost:3002"
    email = "kim.smirnov@gmail.com"
    password = "ComfortHubPass2026!"
    db_password = os.environ.get("POSTGRES_PASSWORD", "comfort-hub-db-pass-2026")

    # Get session tokens
    print("Authenticating Dev Metabase...")
    dev_token = get_session(dev_url, email, password)
    if not dev_token:
        print("Failed to get Dev Metabase session.")
        sys.exit(1)

    print("Authenticating Live Metabase...")
    live_token = get_session(live_url, email, password)
    if not live_token:
        print("Failed to get Live Metabase session.")
        sys.exit(1)

    dev_headers = {"X-Metabase-Session": dev_token, "Content-Type": "application/json"}
    live_headers = {"X-Metabase-Session": live_token, "Content-Type": "application/json"}

    # Get Dev DB ID (first postgres engine) and Live DB ID
    dev_db_id = None
    try:
        r = requests.get(f"{dev_url}/api/database", headers=dev_headers, timeout=10)
        r.raise_for_status()
        for db in r.json().get("data", []):
            if db.get("engine") == "postgres":
                dev_db_id = db["id"]
                break
    except Exception as e:
        print(f"Failed to get Dev database ID: {e}")
        sys.exit(1)

    live_db_id = ensure_postgres_live(live_url, live_headers, db_password)
    if not live_db_id:
        print("Failed to resolve or connect Live database.")
        sys.exit(1)

    # Enable embedding on Live Metabase settings
    for setting in ["enable-embedding", "enable-embedding-static"]:
        try:
            requests.put(f"{live_url}/api/setting/{setting}", headers=live_headers, json={"value": True}, timeout=10).raise_for_status()
        except Exception as e:
            print(f"Warning: Failed to enable Live setting {setting}: {e}")

    # Fetch all dashboards from Dev
    print("Fetching dashboards from Dev Metabase...")
    try:
        r = requests.get(f"{dev_url}/api/dashboard", headers=dev_headers, timeout=10)
        r.raise_for_status()
        dashboards = r.json()
    except Exception as e:
        print(f"Failed to fetch dashboards: {e}")
        sys.exit(1)

    for dash_summary in dashboards:
        dash_id = dash_summary["id"]
        dash_name = dash_summary["name"]
        print(f"\nProcessing Dashboard: '{dash_name}' (ID: {dash_id})")

        # Get detailed dashboard (containing cards list)
        try:
            r = requests.get(f"{dev_url}/api/dashboard/{dash_id}", headers=dev_headers, timeout=10)
            r.raise_for_status()
            dash_detail = r.json()
        except Exception as e:
            print(f"Failed to get Dev dashboard details: {e}")
            continue

        # Get existing dashboards in Live
        try:
            r = requests.get(f"{live_url}/api/dashboard", headers=live_headers, timeout=10)
            r.raise_for_status()
            live_dashboards = r.json()
        except Exception as e:
            print(f"Failed to fetch Live dashboards: {e}")
            continue

        live_dash_id = None
        for ld in live_dashboards:
            if ld["name"] == dash_name:
                live_dash_id = ld["id"]
                break

        if live_dash_id:
            print(f"Found existing Live dashboard (ID: {live_dash_id})")
        else:
            print(f"Creating new Live dashboard...")
            try:
                r = requests.post(f"{live_url}/api/dashboard", headers=live_headers, json={
                    "name": dash_name,
                    "description": dash_detail.get("description", "")
                }, timeout=10)
                r.raise_for_status()
                live_dash_id = r.json()["id"]
                print(f"Created Live dashboard (ID: {live_dash_id})")
            except Exception as e:
                print(f"Failed to create Live dashboard: {e}")
                continue

        # Map Dev Cards -> Live Cards
        card_id_map = {}
        for ordered_card in dash_detail.get("ordered_cards", []):
            card = ordered_card.get("card")
            if not card or "id" not in card:
                continue

            dev_card_id = card["id"]
            card_name = card["name"]

            # Fetch detailed card from Dev
            try:
                r = requests.get(f"{dev_url}/api/card/{dev_card_id}", headers=dev_headers, timeout=10)
                r.raise_for_status()
                dev_card_detail = r.json()
            except Exception as e:
                print(f"Failed to get card detail for '{card_name}': {e}")
                continue

            # Modify query's database reference
            dataset_query = dev_card_detail.get("dataset_query", {})
            if dataset_query.get("database") == dev_db_id:
                dataset_query["database"] = live_db_id

            # Prepare card payload
            card_payload = {
                "name": card_name,
                "dataset_query": dataset_query,
                "display": dev_card_detail.get("display", "table"),
                "visualization_settings": dev_card_detail.get("visualization_settings", {}),
                "description": dev_card_detail.get("description", "")
            }

            # Check if card exists in Live
            try:
                r = requests.get(f"{live_url}/api/card", headers=live_headers, timeout=10)
                r.raise_for_status()
                live_cards = r.json()
            except Exception as e:
                print(f"Failed to fetch Live cards: {e}")
                continue

            live_card_id = None
            for lc in live_cards:
                if lc["name"] == card_name:
                    live_card_id = lc["id"]
                    break

            if live_card_id:
                print(f"  Updating card '{card_name}' (ID: {live_card_id})...")
                try:
                    r = requests.put(f"{live_url}/api/card/{live_card_id}", headers=live_headers, json=card_payload, timeout=10)
                    r.raise_for_status()
                except Exception as e:
                    print(f"  Failed to update card '{card_name}': {e}")
                    continue
            else:
                print(f"  Creating new card '{card_name}'...")
                try:
                    r = requests.post(f"{live_url}/api/card", headers=live_headers, json=card_payload, timeout=10)
                    r.raise_for_status()
                    live_card_id = r.json()["id"]
                except Exception as e:
                    print(f"  Failed to create card '{card_name}': {e}")
                    continue

            card_id_map[dev_card_id] = live_card_id

        # Associate Mapped Cards with Live Dashboard Layout
        print("Configuring Live dashboard layout...")
        cards_payload = []
        for i, ordered_card in enumerate(dash_detail.get("ordered_cards", [])):
            dev_cid = ordered_card.get("card_id")
            if dev_cid not in card_id_map:
                continue
            
            live_cid = card_id_map[dev_cid]
            cards_payload.append({
                "id": -(i + 1),  # Negative IDs specify new items
                "card_id": live_cid,
                "size_x": ordered_card.get("size_x", 4),
                "size_y": ordered_card.get("size_y", 4),
                "col": ordered_card.get("col", 0),
                "row": ordered_card.get("row", 0)
            })

        try:
            r = requests.put(f"{live_url}/api/dashboard/{live_dash_id}/cards", headers=live_headers, json={
                "cards": cards_payload
            }, timeout=10)
            r.raise_for_status()
            print("Dashboard layout sync complete.")
        except Exception as e:
            print(f"Failed to sync dashboard cards: {e}")
            continue

        # Enable Embedding on Dashboard
        try:
            r = requests.put(f"{live_url}/api/dashboard/{live_dash_id}", headers=live_headers, json={
                "enable_embedding": True
            }, timeout=10)
            r.raise_for_status()
            print("Dashboard embedding enabled in Live.")
        except Exception as e:
            print(f"Failed to enable Live dashboard embedding: {e}")

    print("\n====================================================")
    print("SUCCESS: Metabase Dev -> Live dashboard sync complete!")
    print("====================================================")

if __name__ == "__main__":
    main()
