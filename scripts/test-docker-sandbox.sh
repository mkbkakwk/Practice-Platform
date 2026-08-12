#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1

project_name="${SANDBOX_TEST_PROJECT:-practice-platform-docker-sandbox-security}"
compose=(docker compose -p "$project_name" -f docker-compose.sandbox-test.yml)
instance_id="docker-security-it"

cleanup() {
  local original_rc=$?
  local cleanup_rc=0
  trap - EXIT INT TERM
  "${compose[@]}" down --remove-orphans || cleanup_rc=$?

  mapfile -t containers < <(docker container ls -aq \
    --filter "label=com.practice-platform.runner-instance=$instance_id")
  if [[ ${#containers[@]} -gt 0 ]]; then
    docker container rm -f "${containers[@]}" || cleanup_rc=$?
  fi

  mapfile -t volumes < <(docker volume ls -q \
    --filter "label=com.practice-platform.runner-instance=$instance_id")
  if [[ ${#volumes[@]} -gt 0 ]]; then
    docker volume rm "${volumes[@]}" || cleanup_rc=$?
  fi

  if [[ $cleanup_rc -ne 0 ]]; then
    echo "ERROR: Docker sandbox test cleanup failed" >&2
    exit "$cleanup_rc"
  fi
  exit "$original_rc"
}

trap cleanup EXIT INT TERM

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Engine with Compose v2 is required" >&2
  exit 127
fi

echo "==> Building the five fixed sandbox images and Docker security test"
for service in sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image runner-docker-security-test; do
  "${compose[@]}" --profile images build "$service"
done

echo "==> Running real Docker sandbox security tests"
"${compose[@]}" run --rm --no-deps runner-docker-security-test

echo "Docker sandbox security tests: PASSED"
