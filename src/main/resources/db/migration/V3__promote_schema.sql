-- =====================================================================
-- V3__promote_schema.sql
-- Promotes the five synced bubble_* tables to proper named tables with
-- UUID surrogate primary keys (the Bubble text id is preserved as
-- bubble_id for cross-reference during the parallel run), and adds the
-- catalog / orders / bookings tables.
--
-- SEQUENCING: this runs AFTER V2__add_user_scoping.sql (PR #55), which
-- already added auth0_user_id + company_id to bubble_users and the
-- filter-path indexes. Those columns are therefore NOT re-added here.
--
-- SCOPING MODEL: user principal (JWT `sub`) -> bubble_users.auth0_user_id
-- -> company_id (the user's "Representing a Company", stored as the Bubble
-- company text id). Every scoped table carries company_id TEXT and is
-- filtered by it. Normalising company_id into a dedicated `companies`
-- table with a UUID FK is intentionally DEFERRED until company membership
-- (owners/workers lists) is actually synced from Bubble.
--
-- DESTRUCTIVE: renames tables and swaps primary keys. Requires
-- spring.flyway.baseline-on-migrate=true on the existing non-empty DB
-- (set in application.properties by this change). Take a database backup
-- before running. See the PR description for the full runbook.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- USERS (promote bubble_users)
--   auth0_user_id + company_id already exist (added by V2/#55) -> not re-added.
-- ---------------------------------------------------------------------
ALTER TABLE bubble_users RENAME TO users;
ALTER TABLE users RENAME COLUMN id TO bubble_id;
ALTER TABLE users DROP CONSTRAINT bubble_users_pkey;
ALTER TABLE users ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE users ADD PRIMARY KEY (id);
ALTER TABLE users ADD CONSTRAINT users_bubble_id_key UNIQUE (bubble_id);
ALTER TABLE users RENAME COLUMN name TO full_name;
ALTER TABLE users RENAME COLUMN active TO is_active;
ALTER TABLE users ALTER COLUMN max_hours TYPE NUMERIC(5,2) USING max_hours::numeric;
ALTER TABLE users ADD COLUMN email      TEXT;
ALTER TABLE users ADD COLUMN wage_rate  NUMERIC(10,2);
ALTER TABLE users ADD COLUMN created_at TIMESTAMPTZ DEFAULT now();

-- ---------------------------------------------------------------------
-- STORES (promote bubble_stores)
--   existing `company` TEXT (Bubble company id) is the scoping key (#55).
-- ---------------------------------------------------------------------
ALTER TABLE bubble_stores RENAME TO stores;
ALTER TABLE stores RENAME COLUMN id TO bubble_id;
ALTER TABLE stores DROP CONSTRAINT bubble_stores_pkey;
ALTER TABLE stores ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE stores ADD PRIMARY KEY (id);
ALTER TABLE stores ADD CONSTRAINT stores_bubble_id_key UNIQUE (bubble_id);
ALTER TABLE stores ADD COLUMN created_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE stores ALTER COLUMN is_deleted SET DEFAULT false;
-- existing `company` and `availability_id` (Bubble text ids) kept for backfill.

-- ---------------------------------------------------------------------
-- AVAILABILITY (promote bubble_availability — thing_type/thing_id kept)
-- ---------------------------------------------------------------------
ALTER TABLE bubble_availability RENAME TO availability;
ALTER TABLE availability RENAME COLUMN id TO bubble_id;
ALTER TABLE availability DROP CONSTRAINT bubble_availability_pkey;
ALTER TABLE availability ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE availability ADD PRIMARY KEY (id);
ALTER TABLE availability ADD CONSTRAINT availability_bubble_id_key UNIQUE (bubble_id);

-- ---------------------------------------------------------------------
-- WAGE RATES (promote bubble_wage_rates)
--   keep existing text `user_id` (Bubble worker id) as-is for backfill.
-- ---------------------------------------------------------------------
ALTER TABLE bubble_wage_rates RENAME TO wage_rates;
ALTER TABLE wage_rates RENAME COLUMN id TO bubble_id;
ALTER TABLE wage_rates DROP CONSTRAINT bubble_wage_rates_pkey;
ALTER TABLE wage_rates ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE wage_rates ADD PRIMARY KEY (id);
ALTER TABLE wage_rates ADD CONSTRAINT wage_rates_bubble_id_key UNIQUE (bubble_id);

-- ---------------------------------------------------------------------
-- SHIFTS (promote bubble_shifts)
--   existing `assigned_company` TEXT is the scoping key (#55).
-- ---------------------------------------------------------------------
ALTER TABLE bubble_shifts RENAME TO shifts;
ALTER TABLE shifts RENAME COLUMN id TO bubble_id;
ALTER TABLE shifts DROP CONSTRAINT bubble_shifts_pkey;
ALTER TABLE shifts ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE shifts ADD PRIMARY KEY (id);
ALTER TABLE shifts ADD CONSTRAINT shifts_bubble_id_key UNIQUE (bubble_id);

-- ---------------------------------------------------------------------
-- CATEGORIES (new — two-level tree via self-FK)
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id   TEXT UNIQUE,
    company_id  TEXT,                                -- scoping key (Bubble company id)
    name        TEXT NOT NULL,
    parent_id   UUID REFERENCES categories(id),      -- NULL = Main Product level
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- ---------------------------------------------------------------------
-- OFFERINGS (new — created before inventory_offerings link table)
-- ---------------------------------------------------------------------
CREATE TABLE offerings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id           TEXT UNIQUE,
    company_id          TEXT,                        -- scoping key (Bubble company id)
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

-- ---------------------------------------------------------------------
-- INVENTORY (new — product/service catalog, NOT stock quantities)
-- ---------------------------------------------------------------------
CREATE TABLE inventory (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id       TEXT UNIQUE,
    company_id      TEXT,                            -- scoping key (Bubble company id)
    name            TEXT NOT NULL,
    type            TEXT,
    main_product_id UUID REFERENCES categories(id),
    category_id     UUID REFERENCES categories(id),
    is_deleted      BOOLEAN DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- Link: inventory <-> offerings (many-to-many)
CREATE TABLE inventory_offerings (
    inventory_id UUID REFERENCES inventory(id),
    offering_id  UUID REFERENCES offerings(id),
    PRIMARY KEY (inventory_id, offering_id)
);

-- ---------------------------------------------------------------------
-- STOCK (new — per-store quantity)
-- ---------------------------------------------------------------------
CREATE TABLE stock (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   TEXT,                               -- scoping key (Bubble company id)
    store_id     UUID REFERENCES stores(id),
    inventory_id UUID REFERENCES inventory(id),
    quantity     INT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ DEFAULT now(),
    UNIQUE (store_id, inventory_id)
);

-- ---------------------------------------------------------------------
-- ORDERS (new — 6-status kanban)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id       TEXT UNIQUE,
    company_id      TEXT,                            -- scoping key (Bubble company id)
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

-- ---------------------------------------------------------------------
-- BOOKINGS (new — calendar)
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bubble_id      TEXT UNIQUE,
    company_id     TEXT,                             -- scoping key (Bubble company id)
    store_id       UUID REFERENCES stores(id),
    worker_id      UUID REFERENCES users(id),
    customer_email TEXT,
    customer_name  TEXT,
    title          TEXT,
    start_time     TIMESTAMPTZ NOT NULL,
    end_time       TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ DEFAULT now()
);

-- =====================================================================
-- FOLLOW-UPS (separate migrations, not here)
--  - The V2/#55 filter indexes (ix_bubble_users_company_id, etc.) survive
--    the rename but keep their old "bubble_" names; optionally ALTER INDEX
--    ... RENAME for cosmetics.
--  - Backfill: stores.availability_id -> availability UUID FK; merge
--    wage_rates.rate -> users.wage_rate; (optionally) normalise company_id
--    into a companies table with UUID FKs once membership is synced.
--  - Add filter indexes on the new tables' company_id once query patterns
--    settle.
--  - Update Metabase models that reference the old bubble_* table names.
-- =====================================================================
