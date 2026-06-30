-- =====================================================================
-- backfill-2-company_id.sql
-- Populate users.company_id (the data-boundary scoping key) from Bubble's
-- "Representing a Company".
--
-- WHY: every scoped read filters by company_id (shifts.assigned_company,
--      stores.company, users.company_id). A user with no company_id sees
--      nothing. This is the user's *data boundary*; their OWNER/WORKER
--      *role* is resolved separately via the companies.owners[]/workers[]
--      arrays (see backfill note + README).
--
-- WHEN: run ONCE, post-deploy, AFTER Flyway has migrated to v4, and after
--       backfill-1. Table is `users` (renamed from `bubble_users` by V3).
--
-- SAFETY: additive UPDATEs only. Runs in a transaction; review, then COMMIT.
--
-- +-- VERIFY BEFORE TRUSTING -------------------------------------------+
-- | The Bubble JSON key for "Representing a Company" is a GUESS. Fetch  |
-- | one real record:  GET {bubble}/obj/user/<id>  and confirm which of |
-- | these keys actually holds the company id, then use it below:        |
-- |   representing_a_company                                            |
-- |   representing_a_company_custom____merchant                         |
-- |   representing_a_company_custom_merchant                            |
-- |   company  |  company_custom____merchant                           |
-- | (mirrors BubbleUser.companyId — keep the two in sync.) The value is |
-- | a Bubble company text id and must match companies.id / a Bubble     |
-- | company _id.                                                        |
-- +--------------------------------------------------------------------+
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- OPTION A — bulk from a Bubble /user export (staging table)
-- Load (bubble_user_id, company_id) from the verified JSON field above.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE bubble_user_company (
    bubble_id  TEXT NOT NULL,   -- Bubble user _id  (matches users.bubble_id)
    company_id TEXT NOT NULL    -- the VERIFIED "Representing a Company" value
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
