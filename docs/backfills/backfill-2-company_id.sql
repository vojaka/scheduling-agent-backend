-- =====================================================================
-- backfill-2-company_id.sql        *** usually NOT needed — see below ***
-- Populate users.company_id (the data-boundary scoping key) from Bubble's
-- "Representing a Company".
--
-- NOTE: the hourly Bubble sync now sets users.company_id automatically
--       from the "Representing a Company" field (alias confirmed & wired in
--       #64 — see BubbleUser.companyId / BubbleSyncService.upsertUser).
--       Run this script ONLY to (a) seed company_id before the first sync,
--       or (b) fix users left blank because their Bubble field is empty.
--
-- WHY company_id matters: every scoped read filters by it
--      (shifts.assigned_company, stores.company, users.company_id). A user
--      with no company_id sees nothing. This is the user's *data boundary*;
--      their OWNER/WORKER *role* is resolved separately via
--      companies.owners[]/workers[] (keys "Owners"/"Workers (list)", #64).
--
-- WHEN: post-deploy, AFTER Flyway has migrated to v5, and after backfill-1.
--       Table is `users` (renamed from `bubble_users` by V3).
--
-- SAFETY: additive UPDATEs only. Runs in a transaction; review, then COMMIT.
--
-- +-- FIELD SOURCE (confirmed) ----------------------------------------+
-- | The Bubble JSON key for "Representing a Company" is `Representing a |
-- | Company` (confirmed in #64). Its value is a Bubble company text id  |
-- | that must match companies.id (a Bubble company _id). If you re-export|
-- | from Bubble, pull this field per user from GET /obj/user.           |
-- +--------------------------------------------------------------------+
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- OPTION A — bulk from a Bubble /user export (staging table)
-- Load (bubble_id, company_id) from the "Representing a Company" field.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE bubble_user_company (
    bubble_id  TEXT NOT NULL,   -- Bubble user _id  (matches users.bubble_id)
    company_id TEXT NOT NULL    -- the "Representing a Company" value
) ON COMMIT DROP;

-- Load from your /obj/user export, e.g.
-- \copy bubble_user_company FROM 'bubble_user_company.csv' CSV HEADER
-- INSERT INTO bubble_user_company (bubble_id, company_id) VALUES
--   ('1690000000000x000000000000000001', '1680000000000x000000000000000009');

UPDATE users u
SET    company_id = s.company_id
FROM   bubble_user_company s
WHERE  u.bubble_id = s.bubble_id
  AND  (u.company_id IS NULL OR u.company_id <> s.company_id);

-- ---------------------------------------------------------------------
-- OPTION B — explicit per-user mapping
-- ---------------------------------------------------------------------
-- UPDATE users SET company_id = '1680000000000x000000000000000009'
--   WHERE bubble_id = '1690000000000x000000000000000001';

-- ---------------------------------------------------------------------
-- VERIFY (review before COMMIT)
-- ---------------------------------------------------------------------
SELECT count(*) FILTER (WHERE company_id IS NOT NULL) AS scoped,
       count(*) FILTER (WHERE company_id IS NULL)     AS unscoped
FROM   users;

-- Every referenced company should exist in `companies` (populated by sync);
-- rows here mean a user points at a company with no membership data, so the
-- user will resolve to role NONE (OWNER actions stay 403) until sync fills it.
SELECT DISTINCT u.company_id
FROM   users u
LEFT   JOIN companies c ON c.id = u.company_id
WHERE  u.company_id IS NOT NULL
  AND  c.id IS NULL;

COMMIT;
