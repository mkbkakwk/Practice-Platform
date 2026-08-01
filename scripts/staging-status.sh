#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging-common.sh"

frontend_port="$(env_value STAGING_FRONTEND_PORT)"
echo "Project:  $project_name"
echo "Git SHA:  $STAGING_GIT_SHA"
echo "Frontend: http://localhost:$frontend_port"
echo "Health:   http://localhost:$frontend_port/api/health"
echo
"${compose[@]}" ps
echo
"${compose[@]}" images

if "${compose[@]}" exec -T frontend wget -q -O /dev/null http://127.0.0.1/api/health 2>/dev/null; then
  echo
  echo "Frontend-to-backend health: OK"
else
  echo
  echo "Frontend-to-backend health: unavailable"
fi
