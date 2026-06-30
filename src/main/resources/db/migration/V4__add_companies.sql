-- =====================================================================
-- V4__add_companies.sql
-- Syncs the Bubble `company` object so the backend can tell OWNERS from
-- WORKERS. Both see the same company data (scoping is by company), but
-- owners get extra rights (manage workers, generate/commit schedules).
--
-- owners / workers hold Bubble user ids (match users.bubble_id).
-- =====================================================================
CREATE TABLE IF NOT EXISTS companies (
    id      TEXT PRIMARY KEY,
    name    TEXT,
    owners  TEXT[],
    workers TEXT[]
);
