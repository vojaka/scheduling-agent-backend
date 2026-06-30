-- =====================================================================
-- backfill-1-auth0_user_id.sql
-- Map each Auth0 login (JWT `sub`) to its synced user row.
--
-- WHY: CurrentUserService resolves the principal as
--        JWT sub -> users.auth0_user_id -> users.company_id.
--      Until auth0_user_id is set, NOTHING resolves: scoped reads return an
--      empty list and OWNER-only actions return 403 (fail-safe).
--
-- WHEN: run ONCE, post-deploy, AFTER Flyway has migrated to v4.
--       Table is `users` (renamed from `bubble_users` by V3).
--
-- SAFETY: additive UPDATEs only — no deletes. Runs inside a transaction;
--         review the verification SELECTs, then COMMIT (or ROLLBACK).
--
-- +-- VERIFY BEFORE TRUSTING -------------------------------------------+
-- | The `sub` values are owned by Auth0, not Bubble. Pull them from the |
-- | Auth0 Management API:  GET /api/v2/users                            |
-- |   - sub   = the `user_id` field (e.g. "auth0|abc123",              |
-- |             "google-oauth2|...")  -- this is the JWT `sub`.         |
-- |   - email = the `email` field (the join key used by Option A).     |
-- | Confirm BOTH users.email and the Auth0 email are populated before  |
-- | running Option A; otherwise use Option B (explicit, no guessing).  |
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

INSERT INTO auth0_user_map (sub, email) VALUES
  ('auth0|69e4a3fd7e454f6050793da2', 'wepaci4615@sixoplus.com'),
  ('apple|001336.9d1b678ab6694610ae013dba28273df4.1953', 'kim.smirnov@gmail.com'),
  ('apple|001336.f92179e12cfe4ba2932eb5f264276e99.2145', 'fjy7nyb8hp@privaterelay.appleid.com'),
  ('auth0|6a3ae636ffaa52d5c965c64c', 'kim123@hormail.com');


UPDATE users u
SET    auth0_user_id = m.sub
FROM   auth0_user_map m
WHERE  lower(u.email) = lower(m.email)
  AND  u.email IS NOT NULL
  AND  (u.auth0_user_id IS NULL OR u.auth0_user_id <> m.sub);

-- ---------------------------------------------------------------------
-- OPTION B — explicit per-user mapping (safest; no alias/email guessing)
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
