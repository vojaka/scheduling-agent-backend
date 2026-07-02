import os
import sys
import json
import requests
import psycopg2
from psycopg2.extras import execute_values

# Configurations
BUBBLE_BASE_URL = os.environ.get("BUBBLE_API_BASE_URL", "https://comforthub.ee/version-test/api/1.1/obj")
BUBBLE_TOKEN = os.environ.get("BUBBLE_API_TOKEN", "default-bubble-token")
DB_URI = os.environ.get("DB_URI", "postgres://postgres:postgres@localhost:5432/postgres")

TABLES_TO_SYNC = [
    'cartitem', 'mainproduct', 'company', 'address', 'bankaccounts', 'brand',
    'links', 'category', 'cart', 'color_theme', 'colourscheme', 'courierorder',
    'currency', 'deliverycostmatrix', 'notifications', 'events', 'file', 'inventory',
    'addonconfiguration', 'inventoryextensions', 'offerings', 'invoice', 'language',
    'limitations', 'logs', 'nonce', 'order', 'everypaynotifications', 'payments',
    'quickshipperupdates', 'shift', 'stock', 'store', 'planconfiguration', 'plan',
    'transactions', 'verifications', 'viewmatrix', 'wagerate', 'availability', 'user'
]

def sanitize_col_name(name):
    if name == "_id":
        return "id"
    # Replace non-alphanumeric chars with underscore, lowercase
    s = "".join([c if c.isalnum() else "_" for c in name]).lower()
    while "__" in s:
        s = s.replace("__", "_")
    s = s.strip("_")
    if s and s[0].isdigit():
        s = "_" + s
    return s

def map_property_type(prop):
    t = prop.get("type")
    fmt = prop.get("format")
    if t == "boolean":
        return "BOOLEAN"
    elif t == "number" or t == "integer":
        return "DOUBLE PRECISION"
    elif t == "array":
        return "TEXT[]"
    elif t == "object":
        return "JSONB"
    elif t == "string" and fmt == "date-time":
        return "TIMESTAMP WITH TIME ZONE"
    else:
        return "TEXT"

def get_bubble_data(endpoint):
    print(f"Fetching {endpoint} records from Bubble...")
    results = []
    cursor = 0
    headers = {}
    if BUBBLE_TOKEN:
        headers["Authorization"] = f"Bearer {BUBBLE_TOKEN}"
        
    while True:
        url = f"{BUBBLE_BASE_URL}/{endpoint}"
        params = {"cursor": cursor, "limit": 100}
        try:
            response = requests.get(url, headers=headers, params=params, timeout=15)
            response.raise_for_status()
            data = response.json().get("response", {})
            batch = data.get("results", [])
            results.extend(batch)
            remaining = data.get("remaining", 0)
            if remaining > 0:
                cursor += len(batch)
            else:
                break
        except Exception as e:
            print(f"  Error fetching page for {endpoint}: {e}")
            break
            
    print(f"Fetched {len(results)} records.")
    return results

def main():
    print("====================================================")
    # 1. Fetch Swagger definition
    swagger_url = BUBBLE_BASE_URL.replace("/obj", "/meta/swagger.json")
    print(f"Fetching Bubble Swagger definitions from {swagger_url}...")
    headers = {}
    if BUBBLE_TOKEN:
        headers["Authorization"] = f"Bearer {BUBBLE_TOKEN}"
    try:
        r = requests.get(swagger_url, headers=headers, timeout=15)
        r.raise_for_status()
        swagger = r.json()
    except Exception as e:
        print(f"Failed to fetch swagger.json: {e}")
        sys.exit(1)

    definitions = swagger.get("definitions", {})

    schema_changes = []

    # Connect to PostgreSQL database
    try:
        conn = psycopg2.connect(DB_URI)
        conn.autocommit = True
    except Exception as e:
        print(f"Failed to connect to database: {e}")
        sys.exit(1)

    for table in TABLES_TO_SYNC:
        print(f"\nProcessing table: {table}")
        
        # Determine table fields
        properties = definitions.get(table, {}).get("properties", {})
        if not properties:
            print(f"  No properties found in swagger definitions for {table}. Skipping.")
            continue
            
        columns = {}
        for col_name, prop in properties.items():
            db_col = sanitize_col_name(col_name)
            db_type = map_property_type(prop)
            columns[col_name] = (db_col, db_type)
            
        # Ensure id is always present
        if "_id" not in columns:
            columns["_id"] = ("id", "TEXT")

        db_table_name = f"bubble_{table}"

        # Create Table SQL
        col_definitions = [f'"{db_col}" {db_type}' for raw_col, (db_col, db_type) in columns.items()]
        # Set primary key constraint on "id"
        col_definitions_str = ", ".join(col_definitions)
        create_sql = f"CREATE TABLE IF NOT EXISTS {db_table_name} ({col_definitions_str}, CONSTRAINT pk_{db_table_name} PRIMARY KEY (id));"

        # Dynamically add missing columns if table already exists (for schema evolution)
        with conn.cursor() as cur:
            cur.execute(f"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '{db_table_name}')")
            table_exists = cur.fetchone()[0]
            if not table_exists:
                cur.execute(create_sql)
                schema_changes.append(f"🆕 Created Table: **{db_table_name}**")
            else:
                print(f"  Table {db_table_name} already exists. Checking for missing columns...")
                for raw_col, (db_col, db_type) in columns.items():
                    cur.execute(f"SELECT EXISTS (SELECT FROM information_schema.columns WHERE table_name = '{db_table_name}' AND column_name = '{db_col}')")
                    col_exists = cur.fetchone()[0]
                    if not col_exists:
                        print(f"    Adding missing column: {db_col} {db_type}")
                        cur.execute(f'ALTER TABLE {db_table_name} ADD COLUMN "{db_col}" {db_type}')
                        schema_changes.append(f"➕ Added Column to **{db_table_name}**: `{db_col}` ({db_type})")

        # Fetch Data
        data = get_bubble_data(table)
        if not data:
            print("  No records to sync.")
            continue

        # Prepare Batch Upsert SQL
        col_names = [db_col for raw_col, (db_col, db_type) in columns.items()]
        col_names_escaped = [f'"{db_col}"' for db_col in col_names]
        col_names_str = ", ".join(col_names_escaped)
        val_placeholders = ", ".join(["%s"] * len(col_names))
        
        # Conflict statement: ON CONFLICT (id) DO UPDATE SET ...
        update_statements = [f'"{db_col}" = EXCLUDED."{db_col}"' for db_col in col_names if db_col != "id"]
        update_statements_str = ", ".join(update_statements)
        
        upsert_sql = f"""
            INSERT INTO {db_table_name} ({col_names_str})
            VALUES ({val_placeholders})
            ON CONFLICT (id)
            DO UPDATE SET {update_statements_str};
        """

        # Map batch data
        batch_values = []
        for record in data:
            row = []
            for raw_col, (db_col, db_type) in columns.items():
                val = record.get(raw_col)
                
                # Format specific data types
                if db_type == "JSONB" and val is not None:
                    val = json.dumps(val)
                elif db_type == "TEXT[]" and val is not None:
                    # psycopg2 handles python lists directly for PG arrays
                    if not isinstance(val, list):
                        val = [str(val)]
                row.append(val)
            batch_values.append(row)

        # Execute Upsert in transactions
        print(f"  Upserting {len(batch_values)} records into {db_table_name}...")
        with conn.cursor() as cur:
            for row in batch_values:
                try:
                    cur.execute(upsert_sql, row)
                except Exception as e:
                    print(f"    Error upserting row (ID: {row[col_names.index('id')]}): {e}")
                    conn.rollback()
                    continue

        print(f"  Completed sync for {db_table_name}.")

    # Grant permissions to anon role
    with conn.cursor() as cur:
        print("\nGranting permissions to PostgREST anon web role...")
        cur.execute("GRANT USAGE ON SCHEMA public TO anon;")
        cur.execute("GRANT ALL ON ALL TABLES IN SCHEMA public TO anon;")
        cur.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon;")

    print("\n====================================================")
    print("SUCCESS: Synced all Bubble tables to PostgreSQL!")
    print("====================================================")

    # Send Schema Drift webhook notification if changes detected
    if schema_changes:
        webhook_url = os.environ.get("DEPLOY_WEBHOOK_URL")
        if webhook_url:
            print("Schema changes detected. Sending alert to Webhook...")
            changes_str = "\n".join(schema_changes)
            payload = {
                "content": f"🛡️ **Bubble.io Schema Drift Alert: SQL tables altered**\n{changes_str}"
            }
            try:
                requests.post(webhook_url, json=payload, timeout=10)
            except Exception as e:
                print(f"Failed to send schema alert to webhook: {e}")
        else:
            print("Schema changes detected but DEPLOY_WEBHOOK_URL is not set.")

if __name__ == "__main__":
    main()
