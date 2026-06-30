-- =====================================================================
-- V2__add_user_scoping.sql
-- Additive, non-destructive: introduces the columns needed to scope
-- every query by the authenticated user, resolved via their company.
--
-- Scoping model (user principal, company-derived data boundary):
--   JWT `sub`  ->  bubble_users.auth0_user_id  ->  bubble_users.company_id
--   then rows are filtered by that company id:
--     bubble_stores.company, bubble_shifts.assigned_company,
--     bubble_wage_rates.company, bubble_users.company_id (co-workers).
--
-- A user who is in a company's WORKERS list (vs OWNERS) still represents
-- the same company, so the data boundary is identical for both; the
-- owner-vs-worker RIGHTS distinction is a separate authorization layer
-- (not in this migration).
--
-- No table renames / PK changes here (unlike the destructive #54 V2) —
-- safe to apply on the live DB; ships independently of that migration.
-- =====================================================================

-- Map an Auth0 identity (JWT sub) to a synced Bubble user.
ALTER TABLE bubble_users ADD COLUMN IF NOT EXISTS auth0_user_id TEXT;
-- The user's "Representing a Company" — the scoping key.
ALTER TABLE bubble_users ADD COLUMN IF NOT EXISTS company_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS ux_bubble_users_auth0_user_id
    ON bubble_users (auth0_user_id) WHERE auth0_user_id IS NOT NULL;

-- Filter-path indexes.
CREATE INDEX IF NOT EXISTS ix_bubble_users_company_id   ON bubble_users (company_id);
CREATE INDEX IF NOT EXISTS ix_bubble_stores_company     ON bubble_stores (company);
CREATE INDEX IF NOT EXISTS ix_bubble_shifts_company     ON bubble_shifts (assigned_company);
CREATE INDEX IF NOT EXISTS ix_bubble_wage_rates_company ON bubble_wage_rates (company);

-- =====================================================================
-- AFTER DEPLOY — required to make scoping return data:
-- 1. Populate bubble_users.auth0_user_id: map each Auth0 login to its
--    bubble_users row (the JWT `sub`, e.g. 'auth0|abc123').
-- 2. Populate bubble_users.company_id from Bubble's "Representing a
--    Company" field. If the sync alias (BubbleUser.companyId) matches the
--    real Bubble JSON key, the hourly sync fills it; otherwise backfill
--    once with SQL.
-- Until both are set, scoped endpoints correctly return an empty list.
-- =====================================================================
