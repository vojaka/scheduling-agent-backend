#!/bin/bash
set -e
cd /app/scheduling-agent-backend
export $(grep -v '^#' /app/scheduling-agent-backend/.env | xargs)
export DB_URI="postgres://postgres:${POSTGRES_PASSWORD}@localhost:5432/postgres"
export BUBBLE_API_BASE_URL="https://comforthub.ee/version-test/api/1.1/obj"
/usr/bin/python3 sync_all.py
