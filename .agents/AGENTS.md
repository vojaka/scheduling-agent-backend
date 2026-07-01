# ComfortHub Backoffice Project Rules

Whenever a deployment command (such as `scp`, `docker compose build/restart` on the VPS, `git push origin main` or other production/dev deploy action) is executed for the backend or frontend of the ComfortHub Backoffice project:
1. You MUST immediately analyze the changes made in the codebase since the last recorded milestone.
2. You MUST update the repo-root `context.md` (the committed, portable source of truth used in remote / CI sessions) to reflect any new milestones, modified database migrations, or added/changed API endpoints. On the maintainer's local machine, also mirror it to `/Users/kimsmirnov/.gemini/antigravity/scratch/context.md` when that path is available.
