#!/usr/bin/env bash
# Deploy a specific image tag to the VPS
# Usage: ./scripts/deploy.sh <image_tag>
# Example: ./scripts/deploy.sh a84f093

set -euo pipefail

IMAGE_TAG="${1:?Usage: $0 <image_tag>}"
COMPOSE_FILE="docker-compose.prod.yml"
APP_DIR="/opt/riskboard"

echo "==> Deploying RiskBoard tag: $IMAGE_TAG"

cd "$APP_DIR"

# Save current tag for potential rollback
if [ -f .current_tag ]; then
  cp .current_tag .previous_tag
  echo "    Previous tag: $(cat .previous_tag)"
fi
echo "$IMAGE_TAG" > .current_tag

# Pull images with specific tag
IMAGE_TAG="$IMAGE_TAG" docker compose -f "$COMPOSE_FILE" pull backend frontend

# Restart only backend and frontend (postgres stays up)
IMAGE_TAG="$IMAGE_TAG" docker compose -f "$COMPOSE_FILE" up -d --no-deps backend frontend

# Clean dangling images
docker image prune -f

echo "==> Deployment complete: $IMAGE_TAG — $(date)"
