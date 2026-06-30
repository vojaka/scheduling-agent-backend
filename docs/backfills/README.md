# Post-deploy backfills

After the **V1→V5** schema migration runs, exactly **one** manual backfill is
required: **`auth0_user_id`** (backfill-1). The other scoping key,
`users.company_id`, is now populated **automatically by the hourly Bubble sync**
— the field alias was confirmed and wired in **#64** — so backfill-2 remains only
as an initial seed / fallback for users whose "Representing a Company" is blank
in Bubble.

**Until `auth0_user_id` is set, scoping resolves to nothing**: `CurrentUserService`
can't map a JWT `sub` to a user — every scoped read returns an **empty list** and
every OWNER-only action returns **403** (the fail-safe).

Run order (post-deploy, after Flyway has reached **v5**):

1. [`backfill-1-auth0_user_id.sql`](./backfill-1-auth0_user_id.sql) — **REQUIRED.**
   Map each Auth0 login (`sub`) to its `users` row. `auth0_user_id` is never
   sourced from Bubble, so it must be set here. Option A joins on `users.email`
   (auto-synced from Bubble since #64) = the Auth0 email; Option B is explicit
   per-user.
2. [`backfill-2-company_id.sql`](./backfill-2-company_id.sql) — **usually NOT
   needed.** The sync sets `users.company_id` from Bubble's *"Representing a
   Company"* automatically. Run only to seed before the first sync, or to fix
   users left with a blank value.

> **Table name:** V3 renamed `bubble_users` → `users`. These scripts target the
> **post-V3** schema (`users`, with `bubble_id`, `auth0_user_id`, `company_id`,
> `email`). Run them against the migrated DB, not the old `bubble_*` tables.

## Bubble / Auth0 field aliases — CONFIRMED & wired (#64)

These keys were guesses in the original draft; **#64 confirmed them against live
API responses** and wired them into `BubbleUser` / `BubbleSyncService`. Listed
here for reference (and so a future change keeps the SQL and code in sync):

| Value | Source API | Confirmed JSON key |
|---|---|---|
| Auth0 `sub` | Auth0 Mgmt API `GET /api/v2/users` | `user_id` (e.g. `auth0\|abc123`); join on `email` |
| `users.full_name` | Bubble `GET /obj/user` | `FullName` |
| `users.email` | Bubble `GET /obj/user` | nested `authentication.email.email` |
| `users.company_id` | Bubble `GET /obj/user` | `Representing a Company` |
| `companies.name` | Bubble `GET /obj/company` | `Brand Company Name` |
| `companies.owners[]` | Bubble `GET /obj/company` | `Owners` |
| `companies.workers[]` | Bubble `GET /obj/company` | `Workers (list)` |

## Role resolution depends on the `companies` table

backfill-2 (or, normally, the sync) sets a user's **data boundary**
(`company_id`). Their **role** (OWNER vs WORKER) is resolved separately by
matching `users.bubble_id` against `companies.owners[]` / `companies.workers[]`,
populated by the hourly company sync (keys `Owners` / `Workers (list)`, confirmed
#64). Confirm `companies` has rows with **non-empty** owners/workers arrays after
the first sync, or every user resolves to `NONE` and OWNER-only actions stay 403.
