# ComfortHub Backoffice — Project Context

Running context log for the backend. Per `.agents/AGENTS.md`, this file is
updated whenever a deploy-class action (push to a branch, opening/updating a PR,
VPS deploy) changes a **milestone**, a **database migration**, or an **API
endpoint**. The AGENTS.md rule references a local Antigravity scratch path
(`/Users/kimsmirnov/.gemini/antigravity/scratch/context.md`) that only exists on
the maintainer's machine; this repo-root copy is the portable, committed source
of truth used in remote / CI sessions.

---

## Latest milestone — Owner-only worker management (PR #74)

Branch `claude/ui-pr-9-backend-handoff-lrf3lx` · pairs with UI PR
`comforthub-backoffice-ui#9`.

**Added API endpoints** (both OWNER-only, company-scoped):

- `POST /api/users/invite` — invite a worker/owner into the caller's company.
  Body `{ email (required), name?, role? "Worker"|"Owner", maxHours? }`.
  Creates a pending membership (`is_active=false`, no `bubble_id`) and
  best-effort emails an Auth0 account-setup link. `201` →
  `{ id, name, role, maxHours, active, email }`. Errors: `400` invalid email,
  `409` already a member of the company, `403` non-owner.
- `PUT /api/users/{id}` — partial update `{ name?, role?, maxHours?, active? }`.
  `email` is not editable; `active:false` soft-deactivates. `404` when the id is
  outside the caller's company; `403` non-owner.

**Contract decisions** (so the UI can reconcile):
- Requests/responses use title-case roles `"Worker"`/`"Owner"`, mapped to/from
  the stored role strings (`"Worker"`, and `"Merchant"`/`"Admin"` = owner) by
  `dto/RoleMapping`. An invited `"Owner"` is stored as `"Merchant"`.
- Responses use the compact `dto/WorkerResponse` shape. The pre-existing
  `GET /api/users` (in `DataController`) still returns the raw `BubbleUserEntity`
  and was intentionally left unchanged.

**Auth0 invite:** `service/WorkerInvitationService` +
`Auth0WorkerInvitationService` (Management API via `RestClient`). Config-gated —
a no-op until `AUTH0_MGMT_DOMAIN` / `AUTH0_MGMT_CLIENT_ID` /
`AUTH0_MGMT_CLIENT_SECRET` are set (see `.env.example`). Best-effort: a dispatch
failure never blocks the membership.

**Migrations:** none — the `users` table already carries `role`, `max_hours`,
`is_active`, `email`, `auth0_user_id`, `company_id` (from V2/V3).

**Tests:** `UserControllerTest` (10 `@WebMvcTest` cases). `mvn -B clean verify`
→ 34 tests, 0 failures; CI green on `b2e3f5a`.

---

## Scoping & roles model

- Principal resolution: JWT `sub` → `users.auth0_user_id` → `users.company_id`
  (the scoping key every query filters on). See `service/CurrentUserService`.
- Role derivation: stored `"Merchant"`/`"Admin"` → **OWNER**, `"Worker"` →
  **WORKER**, otherwise **NONE**. `requireOwner()` throws `ForbiddenException`
  → `403` via `exception/GlobalExceptionHandler`.
- Data boundary is per-company (owners and workers of a company see the same
  rows); role only gates extra OWNER actions (manage workers, generate/commit
  schedules).

---

## API surface (by controller)

- `MeController` `/api` — `GET /me`, `GET /me/companies`, `POST /me/company`.
- `CompanyController` `/api/companies` — `GET`, `PUT /{id}` (owner),
  `DELETE /{id}` (owner).
- `DataController` `/api` — read-only `GET /shifts`, `GET /users`, `GET /stores`
  (company-scoped, `Pageable`).
- `UserController` `/api/users` — `POST /invite` (owner), `PUT /{id}` (owner). *(new — PR #74)*
- `ScheduleController` `/api/schedule` — `GET /dashboard-url`, `POST /sync`,
  `POST /generate` (owner), `POST /commit` (owner).
- Phase-5 Bubble-proxy controllers (CRUD proxied to the Bubble Data API):
  `ShiftController` `/api/shifts`, `BookingController` `/api/bookings`,
  `CategoryController` `/api/categories`, `InventoryController` `/api/inventory`,
  `OfferingController` `/api/offerings`, `OrderController` `/api/orders`,
  `StockController` `/api/stock`.

All `/api/**` routes require a valid Auth0 JWT (`security/SecurityConfig`).

---

## Database migrations (`src/main/resources/db/migration`)

- `V1__init_schema.sql` — initial `bubble_*` tables + PostgREST `anon` role.
- `V2__add_user_scoping.sql` — `auth0_user_id` + `company_id` on users; filter
  indexes; partial unique index on `auth0_user_id`.
- `V3__promote_schema.sql` — promote `bubble_*` → named tables with UUID PKs
  (Bubble id kept as `bubble_id`); add `categories`, `offerings`, `inventory`,
  `inventory_offerings`, `stock`, `orders`, `bookings`; `users` gains `email`,
  `wage_rate`, `created_at`.
- `V4__add_companies.sql` — `companies` table (`owners[]` / `workers[]` arrays).
- `V5__add_company_reg_code.sql` — `reg_code` on `companies`.
- `V6__add_company_is_deleted.sql` — `is_deleted` on `companies`.

Flyway owns the schema; JPA is `validate`-only.
