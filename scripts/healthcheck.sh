#!/usr/bin/env bash
# Verify all services are healthy after deployment
# Usage: ./scripts/healthcheck.sh [backend_port] [frontend_port]

set -euo pipefail

BACKEND_PORT="${1:-8081}"
FRONTEND_PORT="${2:-4201}"
MAX_ATTEMPTS=12
SLEEP=5

echo "==> Health check — backend :$BACKEND_PORT, frontend :$FRONTEND_PORT"

# ── Backend ──────────────────────────────────────────────────────────────────
echo "--- Backend /actuator/health"
for i in $(seq 1 $MAX_ATTEMPTS); do
  STATUS=$(curl -sf "http://localhost:$BACKEND_PORT/actuator/health" 2>/dev/null | grep -o '"status":"UP"' || true)
  if [ "$STATUS" = '"status":"UP"' ]; then
    echo "    Backend UP"
    break
  fi
  if [ "$i" -eq "$MAX_ATTEMPTS" ]; then
    echo "    ERROR: Backend not ready after $((MAX_ATTEMPTS * SLEEP))s"
    docker logs riskboard-backend-1 --tail 30
    exit 1
  fi
  echo "    Attempt $i/$MAX_ATTEMPTS — waiting ${SLEEP}s..."
  sleep $SLEEP
done

# ── Frontend ─────────────────────────────────────────────────────────────────
echo "--- Frontend HTTP"
for i in $(seq 1 $MAX_ATTEMPTS); do
  CODE=$(curl -so /dev/null -w "%{http_code}" "http://localhost:$FRONTEND_PORT/" 2>/dev/null || true)
  if [ "$CODE" = "200" ]; then
    echo "    Frontend UP (HTTP 200)"
    break
  fi
  if [ "$i" -eq "$MAX_ATTEMPTS" ]; then
    echo "    ERROR: Frontend returned HTTP $CODE after $((MAX_ATTEMPTS * SLEEP))s"
    exit 1
  fi
  echo "    Attempt $i/$MAX_ATTEMPTS — HTTP $CODE — waiting ${SLEEP}s..."
  sleep $SLEEP
done

echo "==> All services healthy"
