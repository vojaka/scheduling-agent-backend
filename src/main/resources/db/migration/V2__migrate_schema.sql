-- =====================================================================
-- V2__migrate_schema.sql
-- Promote the five synced bubble_* tables to proper named tables with
-- UUID primary keys (Bubble text id kept as bubble_id for cross-reference
-- during the parallel run), and add catalog / orders / bookings tables.
--
-- Ships together with: entity remaps, repository findByBubbleId, the
-- BubbleSyncService upsert-by-bubble_id rewrite, and Flyway baseline config.
-- Requires spring.flyway.baseline-on-migrate=true (existing non-empty DB).
-- Take a database backup before running. See PR description.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- COMPANIES (new) ------------------------------------------------------
CREATE TABLE companies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id   TEXT UNIQUE,
    name        TEXT NOT NULL,
    reg_code    TEXT,
    is_active   BOOLEAN DEFAULT true,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- USERS (promote bubble_users) ----------------------------------------
ALTER TABLE bubble_users RENAME TO users;
ALTER TABLE users RENAME COLUMN id TO bubble_id;
ALTER TABLE users DROP CONSTRAINT bubble_users_pkey;
ALTER TABLE users ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE users ADD PRIMARY KEY (id);
ALTER TABLE users ADD CONSTRAINT users_bubble_id_key UNIQUE (bubble_id);
ALTER TABLE users RENAME COLUMN name TO full_name;
ALTER TABLE users RENAME COLUMN active TO is_active;
ALTER TABLE users ALTER COLUMN max_hours TYPE NUMERIC(5,2);
ALTER TABLE users ADD COLUMN auth0_user_id TEXT UNIQUE;
ALTER TABLE users ADD COLUMN company_id    UUID REFERENCES companies(id);
ALTER TABLE users ADD COLUMN email         TEXT;
ALTER TABLE users ADD COLUMN wage_rate     NUMERIC(10,2);
ALTER TABLE users ADD COLUMN created_at    TIMESTAMPTZ DEFAULT now();

-- STORES (promote bubble_stores) --------------------------------------
ALTER TABLE bubble_stores RENAME TO stores;
ALTER TABLE stores RENAME COLUMN id TO bubble_id;
ALTER TABLE stores DROP CONSTRAINT bubble_stores_pkey;
ALTER TABLE stores ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE stores ADD PRIMARY KEY (id);
ALTER TABLE stores ADD CONSTRAINT stores_bubble_id_key UNIQUE (bubble_id);
ALTER TABLE stores ADD COLUMN company_id UUID REFERENCES companies(id);
ALTER TABLE stores ADD COLUMN created_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE stores ALTER COLUMN is_deleted SET DEFAULT false;
-- existing `company` and `availability_id` (Bubble text ids) kept for backfill.

-- AVAILABILITY (promote bubble_availability) --------------------------
ALTER TABLE bubble_availability RENAME TO availability;
ALTER TABLE availability RENAME COLUMN id TO bubble_id;
ALTER TABLE availability DROP CONSTRAINT bubble_availability_pkey;
ALTER TABLE availability ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE availability ADD PRIMARY KEY (id);
ALTER TABLE availability ADD CONSTRAINT availability_bubble_id_key UNIQUE (bubble_id);

-- WAGE RATES (promote bubble_wage_rates) ------------------------------
ALTER TABLE bubble_wage_rates RENAME TO wage_rates;
ALTER TABLE wage_rates RENAME COLUMN id TO bubble_id;
ALTER TABLE wage_rates DROP CONSTRAINT bubble_wage_rates_pkey;
ALTER TABLE wage_rates ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE wage_rates ADD PRIMARY KEY (id);
ALTER TABLE wage_rates ADD CONSTRAINT wage_rates_bubble_id_key UNIQUE (bubble_id);

-- SHIFTS (promote bubble_shifts) --------------------------------------
ALTER TABLE bubble_shifts RENAME TO shifts;
ALTER TABLE shifts RENAME COLUMN id TO bubble_id;
ALTER TABLE shifts DROP CONSTRAINT bubble_shifts_pkey;
ALTER TABLE shifts ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE shifts ADD PRIMARY KEY (id);
ALTER TABLE shifts ADD CONSTRAINT shifts_bubble_id_key UNIQUE (bubble_id);

-- CATEGORIES (new) ----------------------------------------------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id   TEXT UNIQUE,
    company_id  UUID REFERENCES companies(id),
    name        TEXT NOT NULL,
    parent_id   UUID REFERENCES categories(id),
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- OFFERINGS (new) -----------------------------------------------------
CREATE TABLE offerings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id           TEXT UNIQUE,
    company_id          UUID REFERENCES companies(id),
    name                TEXT NOT NULL,
    type                TEXT,
    status              TEXT DEFAULT 'Active' CHECK (status IN ('Active','Inactive')),
    limited_visibility  BOOLEAN DEFAULT false,
    unlimited_quantity  BOOLEAN DEFAULT false,
    quantity_required   BOOLEAN DEFAULT false,
    delivery_type       TEXT,
    pay_options         TEXT[],
    price_source        TEXT,
    default_type        TEXT,
    created_at          TIMESTAMPTZ DEFAULT now()
);

-- INVENTORY (new) -----------------------------------------------------
CREATE TABLE inventory (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id       TEXT UNIQUE,
    company_id      UUID REFERENCES companies(id),
    name            TEXT NOT NULL,
    type            TEXT,
    main_product_id UUID REFERENCES categories(id),
    category_id     UUID REFERENCES categories(id),
    is_deleted      BOOLEAN DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE inventory_offerings (
    inventory_id UUID REFERENCES inventory(id),
    offering_id  UUID REFERENCES offerings(id),
    PRIMARY KEY (inventory_id, offering_id)
);

-- STOCK (new) ---------------------------------------------------------
CREATE TABLE stock (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id     UUID REFERENCES stores(id),
    inventory_id UUID REFERENCES inventory(id),
    quantity     INT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ DEFAULT now(),
    UNIQUE (store_id, inventory_id)
);

-- ORDERS (new) --------------------------------------------------------
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id       TEXT UNIQUE,
    company_id      UUID REFERENCES companies(id),
    store_id        UUID REFERENCES stores(id),
    order_nr        TEXT UNIQUE NOT NULL,
    customer_name   TEXT,
    customer_id     UUID,
    type            TEXT,
    amount          NUMERIC(10,2),
    payment_status  TEXT CHECK (payment_status IN ('Paid','Unpaid','Partial')),
    status          TEXT NOT NULL DEFAULT 'not_started'
                    CHECK (status IN (
                        'not_started','planned','preparation_in_progress',
                        'ready_for_pickup','courier_assigned','completed')),
    assigned_to     UUID REFERENCES users(id),
    ready_by        TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- BOOKINGS (new) ------------------------------------------------------
CREATE TABLE bookings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id      TEXT UNIQUE,
    company_id     UUID REFERENCES companies(id),
    store_id       UUID REFERENCES stores(id),
    worker_id      UUID REFERENCES users(id),
    customer_email TEXT,
    customer_name  TEXT,
    title          TEXT,
    start_time     TIMESTAMPTZ NOT NULL,
    end_time       TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ DEFAULT now()
);

-- Follow-up (separate migration, not here): backfill company_id on users/
-- stores from Bubble `company` text ids; remap stores.availability_id to the
-- availability UUID; merge wage_rates.rate into users.wage_rate. Also update
-- Metabase models that reference the old bubble_* table names.
