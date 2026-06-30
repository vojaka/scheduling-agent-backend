# Post-deploy backfills

Two one-time backfills are required after the V1→V4 schema migration runs.
**Until they run, scoping resolves to nothing**: `users.auth0_user_id` is NULL,
so `CurrentUserService` can't map a JWT to a user — every scoped read returns an
**empty list** and every OWNER-only action returns **403** (the fail-safe).

Run order (post-deploy, after Flyway has reached **v4**):

1. [`backfill-1-auth0_user_id.sql`](./backfill-1-auth0_user_id.sql) — map each
   Auth0 login (`sub`) to its synced `users` row.
2. [`backfill-2-company_id.sql`](./backfill-2-company_id.sql) — populate
   `users.company_id` (the data-boundary key) from Bubble's
   *"Representing a Company"*.

> **Table name:** V3 renamed `bubble_users` → `users`. These scripts target the
> **post-V3** schema (`users`, with `bubble_id`, `auth0_user_id`, `company_id`,
> `email`). Run them against the migrated DB, not the old `bubble_*` tables.

## ⚠️ Verify the Bubble/Auth0 field aliases before trusting these

The JSON keys these scripts and the sync code rely on are **guesses** — confirm
them against a live API response before running, or the columns silently stay
NULL:

| Value | Source API | Candidate JSON keys (verify which one is real) |
|---|---|---|
| Auth0 `sub` | Auth0 Mgmt API `GET /api/v2/users` | `user_id` (e.g. `auth0\|abc123`), join on `email` |
| `users.company_id` | Bubble `GET /obj/user` | `representing_a_company`, `representing_a_company_custom____merchant`, `company`, `company_custom____merchant` |
| `companies.owners[]` / `workers[]` | Bubble `GET /obj/company` | `owners`/`list_owners`/`owners_list_user`; `workers`/`list_workers`/`workers_list_user` |

(The company aliases above mirror `BubbleUser.companyId` and
`BubbleSyncService.upsertCompany()`; if you correct one, correct the other.)

## Role resolution depends on the `companies` table

`backfill-2` sets a user's **data boundary** (`company_id`). Their **role**
(OWNER vs WORKER) is resolved separately by matching `users.bubble_id` against
`companies.owners[]` / `companies.workers[]`. That table is populated by the
hourly Bubble→Postgres company sync — confirm it has rows (and that the
owners/workers arrays are non-empty) after the first sync, or every user
resolves to `NONE` and OWNER-only actions stay 403.
