#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging-common.sh"

frontend_port="$(env_value STAGING_FRONTEND_PORT)"
revision_label='org.opencontainers.image.revision'
image_revision() {
  docker inspect --format "{{ index .Config.Labels \"$revision_label\" }}" "$1" 2>/dev/null || true
}

backend_revision="$(image_revision practice-platform-staging-backend)"
worker_revision="$(image_revision practice-platform-staging-worker)"
frontend_revision="$(image_revision practice-platform-staging-frontend)"

echo "Project:  $project_name"
echo "Local checkout SHA: $STAGING_FULL_GIT_SHA"
if metadata_is_full_git_sha "$backend_revision"; then
  echo "Staging deployed SHA: $backend_revision (OCI revision: backend)"
else
  echo "Staging deployed SHA: unavailable (backend OCI revision missing)"
fi
if [[ -n "$backend_revision" && "$backend_revision" != "$worker_revision" || -n "$backend_revision" && "$backend_revision" != "$frontend_revision" ]]; then
  echo "WARNING: deployed component OCI revision mismatch"
fi
if metadata_is_full_git_sha "$backend_revision" && [[ "$STAGING_FULL_GIT_SHA" != "$backend_revision" ]]; then
  echo "NOTICE: local checkout and deployed staging revision differ"
fi
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
