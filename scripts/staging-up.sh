#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging-common.sh"

validate_staging_env
frontend_port="$(env_value STAGING_FRONTEND_PORT)"
running_frontend="$("${compose[@]}" ps --status running -q frontend 2>/dev/null || true)"
if [[ -z "$running_frontend" ]] && ! port_is_available "$frontend_port"; then
  die "staging frontend port $frontend_port is already in use"
fi

echo "Validating staging Compose configuration"
"${compose[@]}" config --quiet

echo "Building staging images for Git $STAGING_FULL_GIT_SHA (tag $STAGING_GIT_SHA)"
"${compose[@]}" build

echo "Starting isolated staging project $project_name"
"${compose[@]}" up -d --wait --wait-timeout 240

"${compose[@]}" exec -T frontend wget -q -O /dev/null http://127.0.0.1/api/health

echo "Staging is healthy:"
echo "  Frontend: http://localhost:$frontend_port"
echo "  Health:   http://localhost:$frontend_port/api/health"
echo "  Git SHA:  $STAGING_FULL_GIT_SHA"
echo "  Image tag: $STAGING_GIT_SHA"
