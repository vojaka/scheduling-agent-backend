# Deploy runbook — Phase 2/3 → production (Hetzner VPS)

Do these **in order**. Steps 1–3 are operational and require credentials/SSH;
they are intentionally not automated.

Target: `178.105.76.235` (Docker Compose behind nginx). The backend is **not**
auto-deployed (no GitHub Action) — it's a manual Docker deploy. Flyway runs the
**destructive V3** migration (table renames + PK swaps) on start.

---

## 0. Pre-flight

- Confirm `main` is green: `mvn clean test` (11 tests) and `mvn clean package -DskipTests`.
- Have SSH access to the VPS and admin access to Bubble, Metabase, and the Postgres box.

---

## 1. Rotate the leaked secrets (BEFORE deploying)

These values were committed and remain in git history, so they must be rotated
at the source — removing them from the tree is not enough.

| Secret | Where to rotate | Then set on the VPS (`.env`) |
|---|---|---|
| `POSTGRES_PASSWORD` | `ALTER USER postgres WITH PASSWORD '…';` on the DB | `POSTGRES_PASSWORD=` (also reused by `DB_PASSWORD`, `MB_DB_PASS`, PGRST URI) |
| `BUBBLE_API_TOKEN` | Bubble → Settings → API → regenerate token | `BUBBLE_API_TOKEN=` |
| `METABASE_EMBED_SECRET` | Metabase → Admin → Embedding → regenerate secret | `METABASE_EMBED_SECRET=` |
| `PGRST_JWT_SECRET` | generate new: `openssl rand -base64 48` | `PGRST_JWT_SECRET=` |
| `APP_API_KEY` | generate new: `openssl rand -hex 24` | `APP_API_KEY=` |
| `SUPABASE_API_KEY` | legacy/unused after the JPA sync rewrite — leave blank | `SUPABASE_API_KEY=` (empty) |

`GEMINI_API_KEY` was never hardcoded (always an env var) — rotate only if you
believe it leaked elsewhere.

Build the VPS `.env` from [`.env.example`](../.env.example) with the new values.
`.env` is gitignored — never commit it.

---

## 2. Deploy the backend

```bash
# on the VPS, in the repo dir
git fetch origin && git checkout main && git pull --ff-only

# 2a. BACK UP THE DB FIRST — V3 renames tables and swaps PKs (hard to reverse)
docker compose exec -T db pg_dump -U postgres postgres > backup_$(date +%F_%H%M).sql
# (or: docker compose exec -T db pg_dumpall -U postgres > backup_all.sql)

# 2b. ensure .env has the rotated secrets (step 1), then build + start
docker compose build backend
docker compose up -d

# 2c. watch Flyway + startup; abort/rollback if validate fails
docker compose logs -f backend
```

Expect in the logs: `Migrating schema "public" to version "1".."4"` →
`Successfully applied … now at version v4`, then the app starts with no
Hibernate `validate` error. If `validate` fails or a migration errors, **stop**
and restore from the 2a backup before retrying.

---

## 3. Post-deploy backfills (scoped reads return empty until done)

First **verify the field aliases** against live data, then run the scripts.

```bash
# verify which JSON key actually carries the company id (user) …
curl -s -H "Authorization: Bearer $BUBBLE_API_TOKEN" \
  "https://comforthub.ee/version-test/api/1.1/obj/user?limit=1" | jq '.response.results[0]'
# … and the owners/workers lists (company)
curl -s -H "Authorization: Bearer $BUBBLE_API_TOKEN" \
  "https://comforthub.ee/version-test/api/1.1/obj/company?limit=1" | jq '.response.results[0]'
```

Adjust the aliases in `BubbleUser.companyId` / `BubbleSyncService.upsertCompany()`
and in the SQL if they differ from the guesses, then run (in order):

```bash
psql "$DB_URL" -f docs/backfills/backfill-1-auth0_user_id.sql   # map Auth0 sub → users
psql "$DB_URL" -f docs/backfills/backfill-2-company_id.sql      # populate users.company_id
```

See [`docs/backfills/README.md`](./backfills/README.md) for the verification
queries (mapped/unmapped counts, uniqueness, orphaned company refs).

**Smoke test** with a real JWT: `GET /api/shifts|users|stores` return only the
caller's company data; an OWNER can `POST /api/schedule/generate`, a WORKER gets
**403**.

---

## 4. Rollback

V3 is not trivially reversible. If anything fails after migration:

1. `docker compose down`
2. Restore the DB from the step-2a backup (`psql -U postgres postgres < backup_*.sql`
   into a freshly recreated DB).
3. Redeploy the previous backend image/tag.
