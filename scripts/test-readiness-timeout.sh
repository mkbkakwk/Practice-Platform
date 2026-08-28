#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1

project_name="${READINESS_TIMEOUT_TEST_PROJECT:-practice-platform-readiness-timeout-test}"
compose=(docker compose -p "$project_name" -f docker-compose.judge-reliability-test.yml)
runner_instance="reliability-runner"
socket_gid_configured=false

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-socket-gid.sh"

cleanup() {
  local original_rc=$?
  local cleanup_rc=0
  trap - EXIT INT TERM
  if [[ "$socket_gid_configured" == "true" ]]; then
    "${compose[@]}" down --remove-orphans || cleanup_rc=$?
  fi

  mapfile -t containers < <(docker container ls -aq \
    --filter "label=com.practice-platform.runner-instance=$runner_instance")
  if [[ ${#containers[@]} -gt 0 ]]; then
    docker container rm -f "${containers[@]}" || cleanup_rc=$?
  fi
  mapfile -t volumes < <(docker volume ls -q \
    --filter "label=com.practice-platform.runner-instance=$runner_instance")
  if [[ ${#volumes[@]} -gt 0 ]]; then
    docker volume rm "${volumes[@]}" || cleanup_rc=$?
  fi

  if [[ $cleanup_rc -ne 0 ]]; then
    echo "ERROR: readiness-timeout test cleanup failed" >&2
    exit "$cleanup_rc"
  fi
  exit "$original_rc"
}
trap cleanup EXIT INT TERM

fail() {
  echo "ERROR: $*" >&2
  "${compose[@]}" ps -a >&2 || true
  "${compose[@]}" logs --no-color --tail=120 backend worker runner >&2 || true
  exit 1
}

status_within_budget() {
  local service="$1" port="$2" endpoint="$3" expected="$4" max_ms="$5"
  local started_at finished_at elapsed_ms status
  started_at="$(date +%s%3N)"
  status="$("${compose[@]}" exec -T "$service" sh -lc \
    "curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 http://127.0.0.1:${port}${endpoint}" || true)"
  finished_at="$(date +%s%3N)"
  elapsed_ms=$((finished_at - started_at))
  [[ "$status" == "$expected" ]] || fail "$service $endpoint expected HTTP $expected, got ${status:-000}"
  (( elapsed_ms < max_ms )) || fail "$service $endpoint took ${elapsed_ms}ms (budget ${max_ms}ms)"
  echo "$service $endpoint: HTTP $status in ${elapsed_ms}ms"
}

wait_for_status() {
  local service="$1" port="$2" endpoint="$3" expected="$4" timeout_seconds="$5"
  local deadline=$((SECONDS + timeout_seconds))
  until (( SECONDS >= deadline )); do
    if "${compose[@]}" exec -T "$service" sh -lc \
      "curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 http://127.0.0.1:${port}${endpoint}" \
      | grep -qx "$expected"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_container_health() {
  local container_id="$1" timeout_seconds="$2"
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$container_id")" == "healthy" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

restart_count() {
  docker inspect --format '{{.RestartCount}}' "$("${compose[@]}" ps -q "$1")"
}

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Engine with Compose v2 is required" >&2
  exit 127
fi

configure_docker_socket_gid
socket_gid_configured=true

echo "==> Building fixed sandbox images"
docker compose -p practice-platform-readiness-images \
  -f docker-compose.sandbox-test.yml --profile images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image

echo "==> Starting isolated readiness test topology"
"${compose[@]}" up -d --build --wait db rabbitmq backend runner worker \
  || fail "isolated readiness topology did not become healthy"

wait_for_status backend 4000 /api/readiness 200 30 || fail "Backend did not become ready"
wait_for_status worker 8081 /api/readiness 200 30 || fail "Worker did not become ready"
backend_restarts_before="$(restart_count backend)"
worker_restarts_before="$(restart_count worker)"

echo "==> Injecting isolated PostgreSQL outage"
db_container_id="$("${compose[@]}" ps -q db)"
[[ -n "$db_container_id" ]] || fail "could not identify isolated PostgreSQL"
# The reliability topology intentionally stores PostgreSQL under tmpfs. Pausing
# the container preserves its migrated schema while producing the same stalled
# dependency behavior seen by a client during an unresponsive database outage.
docker pause "$db_container_id" >/dev/null || fail "could not pause isolated PostgreSQL"
sleep 1

status_within_budget backend 4000 /api/health 200 2000
status_within_budget backend 4000 /api/readiness 503 3000
status_within_budget worker 8081 /api/health 200 2000
status_within_budget worker 8081 /api/readiness 503 3000

[[ "$(restart_count backend)" == "$backend_restarts_before" ]] \
  || fail "Backend restarted during PostgreSQL outage"
[[ "$(restart_count worker)" == "$worker_restarts_before" ]] \
  || fail "Worker restarted during PostgreSQL outage"

echo "==> Restoring isolated PostgreSQL and verifying automatic recovery"
docker unpause "$db_container_id" >/dev/null || fail "could not resume isolated PostgreSQL"
wait_for_container_health "$db_container_id" 30 || fail "isolated PostgreSQL did not recover"
wait_for_status backend 4000 /api/readiness 200 30 || fail "Backend did not recover readiness"
wait_for_status worker 8081 /api/readiness 200 30 || fail "Worker did not recover readiness"
[[ "$(restart_count backend)" == "$backend_restarts_before" ]] \
  || fail "Backend restarted during PostgreSQL recovery"
[[ "$(restart_count worker)" == "$worker_restarts_before" ]] \
  || fail "Worker restarted during PostgreSQL recovery"

echo "Readiness timeout test: PASSED (bounded DB outage response and automatic recovery)"
