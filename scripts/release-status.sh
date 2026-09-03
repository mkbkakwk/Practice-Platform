#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/release-common.sh"

release_require_command docker
release_require_command curl

value_or_default() {
  local key="$1"
  local fallback="$2"
  local value=""
  if [[ -f "$release_env_file" ]]; then
    value="$(release_env_value "$release_env_file" "$key")"
  fi
  printf '%s' "${value:-$fallback}"
}

db_container="$(value_or_default FORMAL_POSTGRES_CONTAINER oj-db)"
rabbit_container="$(value_or_default FORMAL_RABBITMQ_CONTAINER oj-rabbitmq)"
backend_container="$(value_or_default FORMAL_BACKEND_CONTAINER oj-backend)"
worker_container="$(value_or_default FORMAL_WORKER_CONTAINER oj-worker-1)"
runner_container="$(value_or_default FORMAL_RUNNER_CONTAINER oj-runner)"
frontend_container="$(value_or_default FORMAL_FRONTEND_CONTAINER oj-frontend)"
frontend_port="$(value_or_default FORMAL_FRONTEND_PORT 3000)"

echo "Production release status (read-only)"
for container in "$db_container" "$rabbit_container" "$backend_container" "$runner_container" "$worker_container" "$frontend_container"; do
  if ! docker inspect "$container" >/dev/null 2>&1; then
    echo "  $container: missing"
    continue
  fi
  image_id="$(docker inspect --format '{{.Image}}' "$container")"
  state="$(release_container_state "$container")"
  health="$(release_container_health "$container")"
  restarts="$(docker inspect --format '{{.RestartCount}}' "$container")"
  revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image_id" 2>/dev/null || true)"
  echo "  $container: state=$state health=$health restarts=$restarts"
  echo "    image=$image_id revision=${revision:-not-labeled}"
done

frontend_code="$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$frontend_port/")"
health_code="$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$frontend_port/api/health")"
echo "  frontend HTTP: $frontend_code"
echo "  backend health HTTP: $health_code"

if docker inspect "$db_container" >/dev/null 2>&1; then
  flyway_version="$(docker exec -e PGOPTIONS='-c default_transaction_read_only=on' "$db_container" \
    sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"')"
  echo "  Flyway: V$flyway_version"
fi
