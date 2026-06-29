import os
import sys
import json
import requests
import psycopg2
from psycopg2.extras import execute_values

# Configurations from environment variables
BUBBLE_BASE_URL = os.environ.get("BUBBLE_API_BASE_URL", "https://comforthub.ee/version-test/api/1.1/obj")
BUBBLE_TOKEN = os.environ.get("BUBBLE_API_TOKEN", "default-bubble-token")
DATABASE_URL = os.environ.get("SUPABASE_DB_URL")

if not DATABASE_URL:
    print("ERROR: SUPABASE_DB_URL environment variable is required.")
    print("Format: postgresql://postgres.[YOUR-REF]:[PASS]@aws-1-[REGION].pooler.supabase.com:6543/postgres")
    sys.exit(1)

def get_bubble_data(endpoint):
    print(f"Fetching {endpoint} records from Bubble...")
    results = []
    cursor = 0
    headers = {}
    if BUBBLE_TOKEN and BUBBLE_TOKEN != "default-bubble-token":
        headers["Authorization"] = f"Bearer {BUBBLE_TOKEN}"
        
    while True:
        url = f"{BUBBLE_BASE_URL}/{endpoint}"
        params = {"cursor": cursor}
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()
        
        data = response.json().get("response", {})
        batch = data.get("results", [])
        results.extend(batch)
        
        remaining = data.get("remaining", 0)
        if remaining > 0:
            cursor += len(batch)
        else:
            break
            
    print(f"Successfully fetched {len(results)} records for {endpoint}.")
    return results

def setup_database(conn):
    with conn.cursor() as cur:
        print("Setting up database tables in Supabase...")
        
        # 1. Users Table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS bubble_users (
                id TEXT PRIMARY KEY,
                name TEXT,
                role TEXT,
                max_hours INTEGER,
                active BOOLEAN
            );
        """)
        
        # 2. Stores Table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS bubble_stores (
                id TEXT PRIMARY KEY,
                name TEXT,
                company TEXT,
                availability_id TEXT,
                is_deleted BOOLEAN
            );
        """)
        
        # 3. Availability Table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS bubble_availability (
                id TEXT PRIMARY KEY,
                thing_type TEXT,
                thing_id TEXT,
                available_days TEXT[],
                workday_start_hour INTEGER,
                workday_end_hour INTEGER,
                weekend_start_hour INTEGER,
                weekend_end_hour INTEGER
            );
        """)
        
        # 4. Wage Rates Table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS bubble_wage_rates (
                id TEXT PRIMARY KEY,
                company TEXT,
                rate DOUBLE PRECISION,
                user_id TEXT
            );
        """)
        
        # 5. Shifts Table
        cur.execute("""
            CREATE TABLE IF NOT EXISTS bubble_shifts (
                id TEXT PRIMARY KEY,
                assigned_user TEXT,
                start_time TIMESTAMP WITH TIME ZONE,
                end_time TIMESTAMP WITH TIME ZONE,
                notes TEXT,
                assigned_company TEXT,
                type TEXT,
                status TEXT,
                assigned_store TEXT
            );
        """)
        
        conn.commit()
        print("Database schema verified.")

def sync_users(conn, data):
    # Map Bubble API aliases to clean DB columns
    rows = []
    for item in data:
        # Determine role string
        role_obj = item.get("role") or item.get("role_list_option___user_role") or item.get("primary_login_role_option___user_role")
        role = "Worker"
        if isinstance(role_obj, list) and role_obj:
            role = str(role_obj[0])
        elif role_obj:
            role = str(role_obj)
            
        rows.append((
            item.get("_id"),
            item.get("name") or item.get("name_text"),
            role,
            item.get("maxHours") or item.get("maxHours_number") or item.get("maxhours_number"),
            item.get("active") or item.get("active_boolean")
        ))
        
    if not rows:
        return
        
    with conn.cursor() as cur:
        query = """
            INSERT INTO bubble_users (id, name, role, max_hours, active)
            VALUES %s
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                role = EXCLUDED.role,
                max_hours = EXCLUDED.max_hours,
                active = EXCLUDED.active;
        """
        execute_values(cur, query, rows)
        conn.commit()
    print(f"Synced {len(rows)} users.")

def sync_stores(conn, data):
    rows = []
    for item in data:
        rows.append((
            item.get("_id"),
            item.get("store_name_text"),
            item.get("company__single__custom____merchant"),
            item.get("availability_custom_worker_availability"),
            item.get("isdeleted_boolean", False)
        ))
        
    if not rows:
        return
        
    with conn.cursor() as cur:
        query = """
            INSERT INTO bubble_stores (id, name, company, availability_id, is_deleted)
            VALUES %s
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                company = EXCLUDED.company,
                availability_id = EXCLUDED.availability_id,
                is_deleted = EXCLUDED.is_deleted;
        """
        execute_values(cur, query, rows)
        conn.commit()
    print(f"Synced {len(rows)} stores.")

def sync_availability(conn, data):
    rows = []
    for item in data:
        rows.append((
            item.get("_id"),
            item.get("thing_option_things"),
            item.get("thing_id_text"),
            item.get("usual_available_days_list_option_calendar_days"),
            item.get("workday_availability___start_number"),
            item.get("workday_availability___end_number"),
            item.get("weekend_availability___start_number"),
            item.get("weekend_availability___end_number")
        ))
        
    if not rows:
        return
        
    with conn.cursor() as cur:
        query = """
            INSERT INTO bubble_availability (
                id, thing_type, thing_id, available_days, 
                workday_start_hour, workday_end_hour, 
                weekend_start_hour, weekend_end_hour
            ) VALUES %s
            ON CONFLICT (id) DO UPDATE SET
                thing_type = EXCLUDED.thing_type,
                thing_id = EXCLUDED.thing_id,
                available_days = EXCLUDED.available_days,
                workday_start_hour = EXCLUDED.workday_start_hour,
                workday_end_hour = EXCLUDED.workday_end_hour,
                weekend_start_hour = EXCLUDED.weekend_start_hour,
                weekend_end_hour = EXCLUDED.weekend_end_hour;
        """
        execute_values(cur, query, rows)
        conn.commit()
    print(f"Synced {len(rows)} availability profiles.")

def sync_wage_rates(conn, data):
    rows = []
    for item in data:
        rows.append((
            item.get("_id"),
            item.get("Company") or item.get("company") or item.get("company_custom____merchant"),
            item.get("Rate") or item.get("rate") or item.get("rate_number"),
            item.get("User") or item.get("user") or item.get("user_user")
        ))
        
    if not rows:
        return
        
    with conn.cursor() as cur:
        query = """
            INSERT INTO bubble_wage_rates (id, company, rate, user_id)
            VALUES %s
            ON CONFLICT (id) DO UPDATE SET
                company = EXCLUDED.company,
                rate = EXCLUDED.rate,
                user_id = EXCLUDED.user_id;
        """
        execute_values(cur, query, rows)
        conn.commit()
    print(f"Synced {len(rows)} wage rates.")

def sync_shifts(conn, data):
    rows = []
    for item in data:
        rows.append((
            item.get("_id"),
            item.get("Assigned User"),
            item.get("Time - Start Time"),
            item.get("Time - End Time"),
            item.get("notes"),
            item.get("Assigned Company"),
            item.get("Type"),
            item.get("Status"),
            item.get("Assigned Store")
        ))
        
    if not rows:
        return
        
    with conn.cursor() as cur:
        query = """
            INSERT INTO bubble_shifts (
                id, assigned_user, start_time, end_time, 
                notes, assigned_company, type, status, assigned_store
            ) VALUES %s
            ON CONFLICT (id) DO UPDATE SET
                assigned_user = EXCLUDED.assigned_user,
                start_time = EXCLUDED.start_time,
                end_time = EXCLUDED.end_time,
                notes = EXCLUDED.notes,
                assigned_company = EXCLUDED.assigned_company,
                type = EXCLUDED.type,
                status = EXCLUDED.status,
                assigned_store = EXCLUDED.assigned_store;
        """
        execute_values(cur, query, rows)
        conn.commit()
    print(f"Synced {len(rows)} shifts.")

def main():
    print("Connecting to Supabase Database...")
    try:
        conn = psycopg2.connect(DATABASE_URL)
        print("Connected successfully.")
    except Exception as e:
        print(f"CRITICAL: Failed to connect to database: {e}")
        sys.exit(1)
        
    try:
        setup_database(conn)
        
        # Sync each entity
        sync_users(conn, get_bubble_data("user"))
        sync_stores(conn, get_bubble_data("store"))
        sync_availability(conn, get_bubble_data("availability"))
        sync_wage_rates(conn, get_bubble_data("wagerate"))
        sync_shifts(conn, get_bubble_data("shift"))
        
        print("\nAll data synced successfully! Your Supabase database is up to date.")
        
    except Exception as e:
        print(f"\nERROR: Synchronization failed: {e}")
        sys.exit(1)
    finally:
        conn.close()

if __name__ == "__main__":
    main()
