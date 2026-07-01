# ComfortHub Backoffice Project Rules

Whenever a deployment command (such as `scp`, `docker compose build/restart` on the VPS, `git push origin main` or other production/dev deploy action) is executed for the backend or frontend of the ComfortHub Backoffice project:
1. You MUST immediately analyze the changes made in the codebase since the last recorded milestone.
2. You MUST update the `context.md` file located at `/Users/kimsmirnov/claude/Projects/Backoffice/context.md` to reflect any new milestones, modified database migrations, or added/changed API endpoints.
