#!/usr/bin/env bash

set -u
export COMPOSE_DISABLE_ENV_FILE=1

project_name="${COMPOSE_PROJECT_NAME:-practice-platform-test}"
compose_file="${COMPOSE_FILE:-docker-compose.test.yml}"
compose=(docker compose -p "$project_name" -f "$compose_file")

cleanup() {
  local cleanup_rc=0
  echo
  echo "==> Cleaning isolated test project: $project_name"
  "${compose[@]}" down --remove-orphans || cleanup_rc=$?
  if [[ $cleanup_rc -ne 0 ]]; then
    echo "WARN: isolated test cleanup exited with $cleanup_rc" >&2
  fi
}

trap cleanup EXIT INT TERM

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required" >&2
  exit 127
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: docker compose is required" >&2
  exit 127
fi

build_rc=0
startup_rc=0
backend_rc=125
worker_rc=125
frontend_rc=125

echo "==> Building isolated test images"
"${compose[@]}" build || build_rc=$?

if [[ $build_rc -eq 0 ]]; then
  echo "==> Starting isolated PostgreSQL and RabbitMQ"
  "${compose[@]}" up -d --wait test-db test-rabbitmq || startup_rc=$?
fi

if [[ $build_rc -eq 0 && $startup_rc -eq 0 ]]; then
  echo "==> Running backend tests"
  backend_rc=0
  "${compose[@]}" run --rm backend-test || backend_rc=$?

  echo "==> Running worker tests"
  worker_rc=0
  "${compose[@]}" run --rm worker-test || worker_rc=$?

  echo "==> Running frontend lint, authentication tests, and build"
  frontend_rc=0
  "${compose[@]}" run --rm frontend-test || frontend_rc=$?
fi

echo
echo "Docker test summary"
printf '  image build:   %s\n' "$build_rc"
printf '  dependencies:  %s\n' "$startup_rc"
printf '  backend-test:  %s\n' "$backend_rc"
printf '  worker-test:   %s\n' "$worker_rc"
printf '  frontend-test: %s\n' "$frontend_rc"

for rc in "$build_rc" "$startup_rc" "$backend_rc" "$worker_rc" "$frontend_rc"; do
  if [[ $rc -ne 0 ]]; then
    exit "$rc"
  fi
done

exit 0
