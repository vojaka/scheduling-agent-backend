-- Create the anonymous web role for PostgREST
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS bubble_users (
    id TEXT PRIMARY KEY,
    name TEXT,
    role TEXT,
    max_hours INTEGER,
    active BOOLEAN
);

CREATE TABLE IF NOT EXISTS bubble_stores (
    id TEXT PRIMARY KEY,
    name TEXT,
    company TEXT,
    availability_id TEXT,
    is_deleted BOOLEAN
);

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

CREATE TABLE IF NOT EXISTS bubble_wage_rates (
    id TEXT PRIMARY KEY,
    company TEXT,
    rate DOUBLE PRECISION,
    user_id TEXT
);

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

-- Grant schema permissions to the PostgREST anonymous web role
GRANT USAGE ON SCHEMA public TO anon;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon;
