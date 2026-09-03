#!/usr/bin/env bash
# Rollback to the previous image tag
# Usage: ./scripts/rollback.sh
# Or to a specific tag: ./scripts/rollback.sh <image_tag>

set -euo pipefail

APP_DIR="/opt/riskboard"
COMPOSE_FILE="docker-compose.prod.yml"

cd "$APP_DIR"

if [ -n "${1:-}" ]; then
  TARGET_TAG="$1"
  echo "==> Rolling back to specified tag: $TARGET_TAG"
else
  if [ ! -f .previous_tag ]; then
    echo "ERROR: No previous tag found. Cannot rollback automatically."
    echo "       Run: ./scripts/rollback.sh <image_tag>"
    exit 1
  fi
  TARGET_TAG=$(cat .previous_tag)
  echo "==> Rolling back to previous tag: $TARGET_TAG"
fi

IMAGE_TAG="$TARGET_TAG" docker compose -f "$COMPOSE_FILE" up -d --no-deps backend frontend

echo "$TARGET_TAG" > .current_tag
echo "==> Rollback complete: $TARGET_TAG — $(date)"
