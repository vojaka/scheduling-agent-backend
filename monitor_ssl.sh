#!/bin/bash
set -e

DOMAIN="backoffice-dev.comforthub.ee"

# Load env variables on VPS
cd /app/scheduling-agent-backend
if [ -f .env ]; then
  # Export variables while ignoring comments
  export $(grep -v '^#' .env | xargs)
fi

if [ -z "$DEPLOY_WEBHOOK_URL" ]; then
  echo "DEPLOY_WEBHOOK_URL is not set. Skipping SSL monitoring."
  exit 0
fi

echo "Checking SSL certificate expiration for $DOMAIN..."
EXPIRY_DATE=$(echo | openssl s_client -servername "$DOMAIN" -connect "$DOMAIN":443 2>/dev/null | openssl x509 -noout -dates | grep notAfter | cut -d= -f2)

if [ -z "$EXPIRY_DATE" ]; then
  echo "Error: Could not retrieve SSL certificate details for $DOMAIN."
  exit 1
fi

# Detect OS to parse date correctly
if date --version &>/dev/null; then
  # Linux (GNU coreutils)
  EXPIRY_EPOCH=$(date -d "$EXPIRY_DATE" +%s)
else
  # BSD/macOS
  EXPIRY_EPOCH=$(date -j -f "%b %d %T %Y %Z" "$EXPIRY_DATE" +%s)
fi

CURRENT_EPOCH=$(date +%s)
DAYS_LEFT=$(( (EXPIRY_EPOCH - CURRENT_EPOCH) / 86400 ))

echo "SSL Certificate for $DOMAIN expires on $EXPIRY_DATE ($DAYS_LEFT days left)."

if [ "$DAYS_LEFT" -lt 14 ]; then
  echo "Warning: SSL certificate is expiring soon! Sending alert..."
  PAYLOAD=$(cat <<EOF
{
  "content": "⚠️ **Warning: SSL Certificate Expiry Alert**\nDomain: \`${DOMAIN}\` is expiring in **${DAYS_LEFT} days** (on ${EXPIRY_DATE}). Please verify Certbot auto-renewal status."
}
EOF
)
  curl -s -H "Content-Type: application/json" -X POST -d "$PAYLOAD" "$DEPLOY_WEBHOOK_URL" > /dev/null
  echo "Alert sent successfully."
else
  echo "Certificate is healthy."
fi
