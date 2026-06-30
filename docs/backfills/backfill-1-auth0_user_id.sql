-- =====================================================================
-- backfill-1-auth0_user_id.sql            *** REQUIRED post-deploy step ***
-- Map each Auth0 login (JWT `sub`) to its synced user row.
--
-- WHY: CurrentUserService resolves the principal as
--        JWT sub -> users.auth0_user_id -> users.company_id.
--      Until auth0_user_id is set, NOTHING resolves: scoped reads return an
--      empty list and OWNER-only actions return 403 (fail-safe).
--      auth0_user_id is NEVER sourced from Bubble, so it can only be set here.
--
-- WHEN: run ONCE, post-deploy, AFTER Flyway has migrated to v5.
--       Table is `users` (renamed from `bubble_users` by V3).
--
-- SAFETY: additive UPDATEs only — no deletes. Runs inside a transaction;
--         review the verification SELECTs, then COMMIT (or ROLLBACK).
--
-- +-- FIELD SOURCES (confirmed) ---------------------------------------+
-- | sub   = Auth0 Mgmt API GET /api/v2/users -> `user_id`             |
-- |         (e.g. "auth0|abc123", "google-oauth2|...") = the JWT sub.  |
-- | email = Auth0 `email`. The join key for Option A.                 |
-- | users.email is auto-synced from Bubble (authentication.email.email,|
-- | wired in #64), so Option A's email join works once the sync has    |
-- | run. If emails are missing/ambiguous, use Option B.                |
-- +--------------------------------------------------------------------+
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- OPTION A — bulk join on email (Auth0 export -> staging table)
-- Prefer when users.email is reliably populated on both sides.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE auth0_user_map (
    sub   TEXT NOT NULL,   -- Auth0 user_id / JWT sub
    email TEXT NOT NULL    -- Auth0 email (lower-cased on match below)
) ON COMMIT DROP;

-- Load rows from the Auth0 /api/v2/users export. Replace with your real data,
-- e.g. \copy auth0_user_map FROM 'auth0_users.csv' CSV HEADER
-- INSERT INTO auth0_user_map (sub, email) VALUES
--   ('auth0|abc123',         'owner@acme.example'),
--   ('google-oauth2|xyz789', 'worker@acme.example');

UPDATE users u
SET    auth0_user_id = m.sub
FROM   auth0_user_map m
WHERE  lower(u.email) = lower(m.email)
  AND  u.email IS NOT NULL
  AND  (u.auth0_user_id IS NULL OR u.auth0_user_id <> m.sub);

-- ---------------------------------------------------------------------
-- OPTION B — explicit per-user mapping (safest; no email reliance)
-- Map the Bubble user id (users.bubble_id) directly to its Auth0 sub.
-- Use this when emails are missing/ambiguous.
-- ---------------------------------------------------------------------
-- UPDATE users SET auth0_user_id = 'auth0|abc123'
--   WHERE bubble_id = '1690000000000x000000000000000001';
-- UPDATE users SET auth0_user_id = 'google-oauth2|xyz789'
--   WHERE bubble_id = '1690000000000x000000000000000002';

-- ---------------------------------------------------------------------
-- VERIFY (review before COMMIT)
-- ---------------------------------------------------------------------
-- How many users now resolve, and how many are still unmapped:
SELECT count(*) FILTER (WHERE auth0_user_id IS NOT NULL) AS mapped,
       count(*) FILTER (WHERE auth0_user_id IS NULL)     AS unmapped
FROM   users;

-- Guard: auth0_user_id must be unique (a sub maps to exactly one user).
SELECT auth0_user_id, count(*)
FROM   users
WHERE  auth0_user_id IS NOT NULL
GROUP  BY auth0_user_id
HAVING count(*) > 1;   -- expect 0 rows

COMMIT;
