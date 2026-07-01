import os
import datetime
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

def database_exists(cursor, dbname):
    cursor.execute("SELECT 1 FROM pg_database WHERE datname = %s;", (dbname,))
    return cursor.fetchone() is not None

def table_exists(cursor, tablename):
    cursor.execute(
        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = %s);",
        (tablename,)
    )
    return cursor.fetchone()[0]

def run_migration_file(cursor, filepath):
    print(f"Applying migration: {os.path.basename(filepath)}...")
    with open(filepath, "r") as f:
        sql = f.read()
    
    try:
        cursor.execute(sql)
    except Exception as e:
        print(f"Error executing migration script: {e}")
        raise e

def main():
    print("====================================================")
    print("         ComfortHub Dev Database Seeder             ")
    print("====================================================")

    env = load_env()
    db_password = env.get("POSTGRES_PASSWORD", os.environ.get("POSTGRES_PASSWORD", "comfort-hub-db-pass-2026"))
    if db_password == "dummy":
        db_password = "comfort-hub-db-pass-2026"
    
    # Connect to default 'postgres' database to ensure 'postgres_dev' exists
    hosts = ["localhost", "db"]
    postgres_conn = None
    target_host = None
    
    for host in hosts:
        try:
            db_uri = f"postgres://postgres:{db_password}@{host}:5432/postgres"
            postgres_conn = psycopg2.connect(db_uri)
            postgres_conn.autocommit = True
            target_host = host
            break
        except Exception:
            continue
            
    if not postgres_conn:
        print("Error: Could not connect to default 'postgres' database to initialize.")
        sys.exit(1)

    print(f"Connected to postgres database on {target_host}.")
    pg_cursor = postgres_conn.cursor()
    
    # Create postgres_dev if it doesn't exist
    if not database_exists(pg_cursor, "postgres_dev"):
        print("Database 'postgres_dev' does not exist. Creating...")
        pg_cursor.execute("CREATE DATABASE postgres_dev;")
        print("Database 'postgres_dev' created successfully.")
    else:
        print("Database 'postgres_dev' already exists.")
        
    pg_cursor.close()
    postgres_conn.close()

    # Now connect to postgres_dev
    dev_db_uri = f"postgres://postgres:{db_password}@{target_host}:5432/postgres_dev"
    try:
        conn = psycopg2.connect(dev_db_uri)
        conn.autocommit = True
        cursor = conn.cursor()
    except Exception as e:
        print(f"Error connecting to postgres_dev database: {e}")
        sys.exit(1)

    # Apply Flyway migrations if schema is not initialized (checking if 'users' table exists)
    if not table_exists(cursor, "users"):
        print("Database schema 'users' table not found. Applying migrations...")
        migration_dir = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "db", "migration")
        
        # Sequenced list of migrations
        migrations = [
            "V1__init_schema.sql",
            "V2__add_user_scoping.sql",
            "V3__promote_schema.sql",
            "V4__add_companies.sql",
            "V5__add_company_reg_code.sql",
            "V6__add_company_is_deleted.sql",
            "V7__create_payments_tables.sql"
        ]
        
        for mig in migrations:
            mig_path = os.path.join(migration_dir, mig)
            if os.path.exists(mig_path):
                try:
                    run_migration_file(cursor, mig_path)
                except Exception as e:
                    print(f"Error executing migration {mig}: {e}")
                    cursor.close()
                    conn.close()
                    sys.exit(1)
            else:
                print(f"Warning: Migration file not found: {mig_path}")
    else:
        print("Database schema already initialized.")

    # 1. Truncate all tables
    print("Truncating tables...")
    tables_to_truncate = [
        "disputes", "payment_events", "payments", "payment_tokens",
        "bookings", "orders", "stock", "inventory_offerings", "shifts", 
        "inventory", "offerings", "categories", "wage_rates", "stores", 
        "users", "availability", "companies"
    ]
    for table in tables_to_truncate:
        try:
            cursor.execute(f"TRUNCATE TABLE {table} CASCADE;")
            print(f"- Truncated '{table}' successfully.")
        except Exception as e:
            print(f"- Skipping truncate of '{table}' (might not exist): {e}")

    # Generate dates relative to current local time
    today = datetime.date.today()
    tomorrow = today + datetime.timedelta(days=1)
    yesterday = today - datetime.timedelta(days=1)
    
    t_9am = datetime.time(9, 0)
    t_5pm = datetime.time(17, 0)
    t_10am = datetime.time(10, 0)
    t_6pm = datetime.time(18, 0)

    # 2. Insert Companies
    print("\nInserting mock companies...")
    companies = [
        ("clean-shiny-id", "Clean & Shiny Corp", '{"user-owner-1"}', '{"user-worker-1", "user-worker-2"}', "11223344", False),
        ("cozy-services-id", "Cozy Services Ltd", '{"user-owner-2"}', '{"user-worker-3"}', "44332211", False)
    ]
    for cid, name, owners, workers, reg_code, is_deleted in companies:
        try:
            cursor.execute(
                "INSERT INTO companies (id, name, owners, workers, reg_code, is_deleted) VALUES (%s, %s, %s, %s, %s, %s);",
                (cid, name, owners, workers, reg_code, is_deleted)
            )
        except Exception as e:
            print(f"Error inserting company: {e}")

    # 3. Insert Availability Configurations
    print("Inserting availability configurations...")
    availabilities = [
        ("avail-store-clean-1", "store", "store-clean-1", ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], 8, 20, 10, 18),
        ("avail-store-clean-2", "store", "store-clean-2", ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"], 9, 19, 10, 16),
        ("avail-store-cozy-1", "store", "store-cozy-1", ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], 9, 21, 9, 21),
        ("avail-worker-1", "worker", "user-worker-1", ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"], 8, 17, 0, 0),
        ("avail-worker-2", "worker", "user-worker-2", ["Wednesday", "Thursday", "Friday", "Saturday"], 10, 18, 10, 16)
    ]
    for aid, thing_type, thing_id, available_days, w_start, w_end, we_start, we_end in availabilities:
        try:
            cursor.execute(
                "INSERT INTO availability (bubble_id, thing_type, thing_id, available_days, workday_start_hour, workday_end_hour, weekend_start_hour, weekend_end_hour) VALUES (%s, %s, %s, %s, %s, %s, %s, %s);",
                (aid, thing_type, thing_id, available_days, w_start, w_end, we_start, we_end)
            )
        except Exception as e:
            print(f"Error inserting availability: {e}")

    # 4. Insert Users
    print("Inserting mock users...")
    users = [
        ("user-owner-1", "John Owner", "OWNER", 40, True, "john@clean.com", "auth0|owner1", "clean-shiny-id", 0.0),
        ("user-worker-1", "Alice Worker", "WORKER", 40, True, "alice@clean.com", "auth0|worker1", "clean-shiny-id", 15.50),
        ("user-worker-2", "Bob Worker", "WORKER", 35, True, "bob@clean.com", "auth0|worker2", "clean-shiny-id", 16.00),
        ("user-owner-2", "Clara Cozy", "OWNER", 40, True, "clara@cozy.com", "auth0|owner2", "cozy-services-id", 0.0),
        ("user-worker-3", "David Worker", "WORKER", 40, True, "david@cozy.com", "auth0|worker3", "cozy-services-id", 14.00)
    ]
    user_uuid_map = {}
    for bid, name, role, max_hours, active, email, auth0_id, company_id, wage in users:
        try:
            cursor.execute(
                """INSERT INTO users (bubble_id, full_name, role, max_hours, is_active, email, auth0_user_id, company_id, wage_rate) 
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id;""",
                (bid, name, role, max_hours, active, email, auth0_id, company_id, wage)
            )
            user_uuid_map[bid] = cursor.fetchone()[0]
        except Exception as e:
            print(f"Error inserting user {name}: {e}")

    # 5. Insert Stores
    print("Inserting mock stores...")
    stores = [
        ("store-clean-1", "Clean & Shiny HQ", "clean-shiny-id", "avail-store-clean-1", False),
        ("store-clean-2", "Clean & Shiny North Store", "clean-shiny-id", "avail-store-clean-2", False),
        ("store-cozy-1", "Cozy Office Suite", "cozy-services-id", "avail-store-cozy-1", False)
    ]
    store_uuid_map = {}
    for bid, name, company, avail_id, is_deleted in stores:
        try:
            cursor.execute(
                "INSERT INTO stores (bubble_id, name, company, availability_id, is_deleted) VALUES (%s, %s, %s, %s, %s) RETURNING id;",
                (bid, name, company, avail_id, is_deleted)
            )
            store_uuid_map[bid] = cursor.fetchone()[0]
        except Exception as e:
            print(f"Error inserting store {name}: {e}")

    # 6. Insert Wage Rates (bubble mappings)
    print("Inserting wage rates...")
    wage_rates = [
        ("wage-alice", "clean-shiny-id", 15.50, "user-worker-1"),
        ("wage-bob", "clean-shiny-id", 16.00, "user-worker-2"),
        ("wage-david", "cozy-services-id", 14.00, "user-worker-3")
    ]
    for bid, company, rate, user_id in wage_rates:
        try:
            cursor.execute(
                "INSERT INTO wage_rates (bubble_id, company, rate, user_id) VALUES (%s, %s, %s, %s);",
                (bid, company, rate, user_id)
            )
        except Exception as e:
            print(f"Error inserting wage rate: {e}")

    # 7. Insert Shifts
    print("Inserting shifts...")
    dt_today_9am = datetime.datetime.combine(today, t_9am)
    dt_today_5pm = datetime.datetime.combine(today, t_5pm)
    dt_tomorrow_10am = datetime.datetime.combine(tomorrow, t_10am)
    dt_tomorrow_6pm = datetime.datetime.combine(tomorrow, t_6pm)
    
    shifts = [
        ("shift-1", "user-worker-1", dt_today_9am, dt_today_5pm, "Regular morning routine", "clean-shiny-id", "Morning", "Active", "store-clean-1"),
        ("shift-2", "user-worker-2", dt_today_9am, dt_today_5pm, "Supervising inventory delivery", "clean-shiny-id", "Morning", "Active", "store-clean-1"),
        ("shift-3", "user-worker-1", dt_tomorrow_10am, dt_tomorrow_6pm, "Deep cleaning assignment", "clean-shiny-id", "Evening", "Active", "store-clean-2"),
        ("shift-4", "user-worker-3", dt_today_9am, dt_today_5pm, "Cozy salon coverage", "cozy-services-id", "Morning", "Active", "store-cozy-1")
    ]
    for bid, assigned_user, start, end, notes, company, shift_type, status, store in shifts:
        try:
            cursor.execute(
                """INSERT INTO shifts (bubble_id, assigned_user, start_time, end_time, notes, assigned_company, type, status, assigned_store) 
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s);""",
                (bid, assigned_user, start, end, notes, company, shift_type, status, store)
            )
        except Exception as e:
            print(f"Error inserting shift: {e}")

    # 8. Insert Categories
    print("Inserting categories...")
    categories = [
        ("cat-cleaning", "clean-shiny-id", "Cleaning Services", None, 1),
        ("cat-home-cleaning", "clean-shiny-id", "Home Cleaning", "cat-cleaning", 1),
        ("cat-office-cleaning", "clean-shiny-id", "Office Cleaning", "cat-cleaning", 2),
        ("cat-wellness", "cozy-services-id", "Wellness & Spa", None, 1)
    ]
    cat_uuid_map = {}
    for bid, company, name, parent_bid, sort_order in categories:
        parent_id = cat_uuid_map.get(parent_bid) if parent_bid else None
        try:
            cursor.execute(
                "INSERT INTO categories (bubble_id, company_id, name, parent_id, sort_order) VALUES (%s, %s, %s, %s, %s) RETURNING id;",
                (bid, company, name, parent_id, sort_order)
            )
            cat_uuid_map[bid] = cursor.fetchone()[0]
        except Exception as e:
            print(f"Error inserting category {name}: {e}")

    # 9. Insert Offerings
    print("Inserting offerings...")
    offerings = [
        ("offering-std", "clean-shiny-id", "Standard Cleaning 2h", "service", "Active", False, False, False, "on_site", ["card", "cash"], "default", "cleaning"),
        ("offering-deep", "clean-shiny-id", "Deep Cleaning 4h", "service", "Active", False, False, False, "on_site", ["card"], "default", "cleaning"),
        ("offering-massage", "cozy-services-id", "Relaxing Massage 1h", "service", "Active", False, False, False, "on_site", ["card"], "default", "massage")
    ]
    offering_uuid_map = {}
    for bid, company, name, otype, status, lim_vis, un_qty, qty_req, del_type, pay, pr_src, def_type in offerings:
        try:
            cursor.execute(
                """INSERT INTO offerings (bubble_id, company_id, name, type, status, limited_visibility, unlimited_quantity, quantity_required, delivery_type, pay_options, price_source, default_type) 
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id;""",
                (bid, company, name, otype, status, lim_vis, un_qty, qty_req, del_type, pay, pr_src, def_type)
            )
            offering_uuid_map[bid] = cursor.fetchone()[0]
        except Exception as e:
            print(f"Error inserting offering {name}: {e}")

    # 10. Insert Inventory Items
    print("Inserting inventory items...")
    inventory_items = [
        ("inv-std", "clean-shiny-id", "Standard Cleaning", "service", "cat-home-cleaning", False),
        ("inv-deep", "clean-shiny-id", "Deep Cleaning", "service", "cat-home-cleaning", False),
        ("inv-massage", "cozy-services-id", "Relaxing Massage", "service", "cat-wellness", False)
    ]
    inventory_uuid_map = {}
    for bid, company, name, itype, cat_bid, is_deleted in inventory_items:
        cat_id = cat_uuid_map.get(cat_bid)
        try:
            cursor.execute(
                "INSERT INTO inventory (bubble_id, company_id, name, type, category_id, is_deleted) VALUES (%s, %s, %s, %s, %s, %s) RETURNING id;",
                (bid, company, name, itype, cat_id, is_deleted)
            )
            inventory_uuid_map[bid] = cursor.fetchone()[0]
        except Exception as e:
            print(f"Error inserting inventory {name}: {e}")

    # 11. Insert Inventory-Offering relations
    print("Inserting inventory-offering relations...")
    inv_off_relations = [
        ("inv-std", "offering-std"),
        ("inv-deep", "offering-deep"),
        ("inv-massage", "offering-massage")
    ]
    for inv_bid, off_bid in inv_off_relations:
        inv_id = inventory_uuid_map.get(inv_bid)
        off_id = offering_uuid_map.get(off_bid)
        if inv_id and off_id:
            try:
                cursor.execute(
                    "INSERT INTO inventory_offerings (inventory_id, offering_id) VALUES (%s, %s);",
                    (inv_id, off_id)
                )
            except Exception as e:
                print(f"Error inserting inventory_offering relationship: {e}")

    # 12. Insert Stock
    print("Inserting stock...")
    stock = [
        ("clean-shiny-id", "store-clean-1", "inv-std", 99),
        ("clean-shiny-id", "store-clean-1", "inv-deep", 50),
        ("cozy-services-id", "store-cozy-1", "inv-massage", 20)
    ]
    for company_id, store_bid, inv_bid, qty in stock:
        sid = store_uuid_map.get(store_bid)
        iid = inventory_uuid_map.get(inv_bid)
        if sid and iid:
            try:
                cursor.execute(
                    "INSERT INTO stock (company_id, store_id, inventory_id, quantity) VALUES (%s, %s, %s, %s);",
                    (company_id, sid, iid, qty)
                )
            except Exception as e:
                print(f"Error inserting stock: {e}")

    # 13. Insert Bookings
    print("Inserting bookings...")
    dt_today_10am = datetime.datetime.combine(today, t_10am)
    dt_today_12pm = dt_today_10am + datetime.timedelta(hours=2)
    bookings = [
        ("book-1", "clean-shiny-id", "store-clean-1", "user-worker-1", "alice@customer.com", "Alice Customer", "Standard Office Routine", dt_today_10am, dt_today_12pm)
    ]
    for bid, company, store_bid, worker_bid, c_email, c_name, title, start, end in bookings:
        sid = store_uuid_map.get(store_bid)
        wid = user_uuid_map.get(worker_bid)
        try:
            cursor.execute(
                """INSERT INTO bookings (bubble_id, company_id, store_id, worker_id, customer_email, customer_name, title, start_time, end_time) 
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s);""",
                (bid, company, sid, wid, c_email, c_name, title, start, end)
            )
        except Exception as e:
            print(f"Error inserting booking: {e}")

    # 14. Insert Orders (if the table exists in PostgreSQL database)
    print("Inserting orders...")
    orders = [
        ("ord-1", "clean-shiny-id", "store-clean-1", "ORD-2026-0001", "Emma Customer", None, "service", 120.00, "Paid", "completed", "user-worker-1", dt_today_5pm, "No specific details"),
        ("ord-2", "clean-shiny-id", "store-clean-2", "ORD-2026-0002", "Jack Customer", None, "service", 250.00, "Unpaid", "planned", "user-worker-2", dt_tomorrow_6pm, "Please call before arrival")
    ]
    for bid, company, store_bid, order_nr, c_name, c_uuid, otype, amt, pay_stat, status, worker_bid, ready, notes in orders:
        sid = store_uuid_map.get(store_bid)
        wid = user_uuid_map.get(worker_bid)
        try:
            cursor.execute(
                """INSERT INTO orders (bubble_id, company_id, store_id, order_nr, customer_name, customer_id, type, amount, payment_status, status, assigned_to, ready_by, notes) 
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);""",
                (bid, company, sid, order_nr, c_name, c_uuid, otype, amt, pay_stat, status, wid, ready, notes)
            )
        except Exception as e:
            print(f"Error inserting order (table might be deleted): {e}")

    cursor.close()
    conn.close()
    print("\n====================================================")
    print("SUCCESS: Mock database seeding completed!")
    print("====================================================")

if __name__ == "__main__":
    main()
