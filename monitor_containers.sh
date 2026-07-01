#!/bin/bash
set -e

# Load environment variables
cd /app/scheduling-agent-backend
if [ -f .env ]; then
  # Export variables while ignoring comments
  export $(grep -v '^#' .env | xargs)
fi

if [ -z "$DEPLOY_WEBHOOK_URL" ]; then
  echo "DEPLOY_WEBHOOK_URL is not set. Skipping monitoring alert."
  exit 0
fi

ALERT_MSG=""

# Find all containers matching 'scheduling-' prefix
CONTAINERS=$(docker ps -a --format "{{.Names}}" | grep "^scheduling-" || true)

for CONTAINER in $CONTAINERS; do
  # Get running state and health status
  INSPECT=$(docker inspect --format '{{.State.Running}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$CONTAINER" 2>/dev/null || true)
  
  if [ -z "$INSPECT" ]; then
    continue
  fi
  
  RUNNING=$(echo "$INSPECT" | awk '{print $1}')
  HEALTH=$(echo "$INSPECT" | awk '{print $2}')
  
  if [ "$RUNNING" != "true" ]; then
    ALERT_MSG="${ALERT_MSG}- 🔴 **${CONTAINER}** is STOPPED / CRASHED\n"
  elif [ "$HEALTH" = "unhealthy" ]; then
    ALERT_MSG="${ALERT_MSG}- 🟡 **${CONTAINER}** is UNHEALTHY (health checks failing)\n"
  fi
done

if [ -n "$ALERT_MSG" ]; then
  echo -e "Unhealthy containers detected:\n$ALERT_MSG"
  
  # Send POST request to Webhook
  PAYLOAD=$(cat <<EOF
{
  "content": "⚠️ **Alert: Docker Container Downtime Detected on Hetzner VPS**\n${ALERT_MSG}"
}
EOF
)
  curl -s -H "Content-Type: application/json" -X POST -d "$PAYLOAD" "$DEPLOY_WEBHOOK_URL" > /dev/null
  echo "Alert sent successfully."
else
  echo "All containers are healthy."
fi
