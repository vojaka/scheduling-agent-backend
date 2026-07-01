import os
import sys
import psycopg2

def load_env():
    env_vars = {}
    env_path = os.path.join(os.path.dirname(__file__), ".env")
    if os.path.exists(env_path):
        with open(env_path, "r") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    env_vars[k.strip()] = v.strip()
    return env_vars

def main():
    print("====================================================")
    print("      ComfortHub Database Performance Auditor       ")
    print("====================================================")

    env = load_env()
    db_password = env.get("POSTGRES_PASSWORD", os.environ.get("POSTGRES_PASSWORD", "comfort-hub-db-pass-2026"))
    if db_password == "dummy":
        db_password = "comfort-hub-db-pass-2026"

    db_name = env.get("DB_NAME", os.environ.get("DB_NAME", "postgres_dev"))
    # Connect to local database
    hosts = ["localhost", "db"]
    conn = None
    for host in hosts:
        try:
            db_uri = f"postgres://postgres:{db_password}@{host}:5432/{db_name}"
            conn = psycopg2.connect(db_uri)
            break
        except Exception:
            continue

    if not conn:
        print(f"Error: Could not connect to PostgreSQL database {db_name}.")
        sys.exit(1)

    cursor = conn.cursor()

    # 1. Check Database Cache Hit Ratio
    print("\n📊 Database Cache Hit Ratio:")
    print("----------------------------------------------------")
    try:
        cursor.execute("""
            SELECT 
              COALESCE(sum(heap_blks_read), 0) as heap_read,
              COALESCE(sum(heap_blks_hit), 0)  as heap_hit,
              CASE 
                WHEN sum(heap_blks_hit) + sum(heap_blks_read) = 0 THEN 0.0
                ELSE sum(heap_blks_hit)::float / (sum(heap_blks_hit) + sum(heap_blks_read))::float
              END as ratio
            FROM pg_statio_user_tables;
        """)
        heap_read, heap_hit, ratio = cursor.fetchone()
        print(f"Heap Reads (Disk): {heap_read}")
        print(f"Heap Hits (Cache): {heap_hit}")
        print(f"Cache Hit Ratio:   {ratio:.2%} (Target: >99.00%)")
        if ratio < 0.99:
            print("⚠️ Recommendation: Consider allocating more memory (shared_buffers) to Postgres.")
    except Exception as e:
        print(f"Error fetching cache ratio: {e}")

    # 2. Check Unused or Missing Indexes (Sequential Scans Audit)
    print("\n🔍 Table Scan Patterns (Sequential Scans Audit):")
    print("----------------------------------------------------")
    print(f"{'Table Name':<20} | {'Seq Scans':<10} | {'Index Scans':<11} | {'Total Rows':<10}")
    print("----------------------------------------------------")
    try:
        cursor.execute("""
            SELECT relname, seq_scan, idx_scan, n_live_tup
            FROM pg_stat_user_tables
            ORDER BY seq_scan DESC
            LIMIT 10;
        """)
        rows = cursor.fetchall()
        for rname, seq, idx, rows_cnt in rows:
            print(f"{rname:<20} | {seq:<10} | {idx:<11} | {rows_cnt:<10}")
            if seq > 100 and idx < seq:
                print(f"  👉 Recommendation: Table '{rname}' has high sequential scans. Check query filters for missing indexes.")
    except Exception as e:
        print(f"Error checking table scans: {e}")

    # 3. Check Foreign Keys lacking Indexes
    print("\n🔑 Missing Indexes on Foreign Keys (FKs):")
    print("----------------------------------------------------")
    try:
        cursor.execute("""
            SELECT
                tc.table_name, 
                kcu.column_name,
                ccu.table_name AS foreign_table_name,
                ccu.column_name AS foreign_column_name 
            FROM 
                information_schema.table_constraints AS tc 
                JOIN information_schema.key_column_usage AS kcu
                  ON tc.constraint_name = kcu.constraint_name
                  AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage AS ccu
                  ON ccu.constraint_name = tc.constraint_name
                  AND ccu.table_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND NOT EXISTS (
                SELECT 1 
                FROM pg_index i 
                JOIN pg_class c ON c.oid = i.indrelid 
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ANY(i.indkey)
                WHERE c.relname = tc.table_name AND a.attname = kcu.column_name
              );
        """)
        fk_rows = cursor.fetchall()
        if not fk_rows:
            print("✓ All foreign keys are indexed correctly.")
        else:
            print(f"Found {len(fk_rows)} unindexed foreign keys:")
            for tbl, col, ftbl, fcol in fk_rows:
                idx_name = f"idx_{tbl}_{col}"
                print(f"- Table '{tbl}' column '{col}' references '{ftbl}({fcol})' but lacks an index.")
                print(f"  💡 Recommendation: CREATE INDEX {idx_name} ON {tbl}(\"{col}\");")
    except Exception as e:
        print(f"Error checking foreign keys: {e}")

    cursor.close()
    conn.close()
    print("\n====================================================")
    print("Auditing complete!")
    print("====================================================")

if __name__ == "__main__":
    main()
