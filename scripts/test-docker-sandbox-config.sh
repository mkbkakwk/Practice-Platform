#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

service_block() {
  local service="$1"
  awk -v service="$service" '
    $0 == "  " service ":" {inside = 1}
    inside && $0 ~ /^  [a-zA-Z0-9_-]+:$/ && $0 != "  " service ":" {exit}
    inside {print}
  ' docker-compose.yml
}

export RUNNER_TOKEN=formal-compose-config-test-token
export DOCKER_SOCKET_GID=4242

bash ./scripts/test-docker-socket-gid.sh

docker compose -f docker-compose.yml config --quiet

services="$(docker compose -f docker-compose.yml config --services)"
for required in db rabbitmq backend frontend runner worker; do
  grep -Fxq "$required" <<<"$services" || fail "formal Compose service missing: $required"
done

images="$(docker compose -f docker-compose.yml --profile sandbox-images config --images)"
for required in \
  practice-sandbox-python:local \
  practice-sandbox-javascript:local \
  practice-sandbox-c:local \
  practice-sandbox-cpp17:local \
  practice-sandbox-java:local; do
  grep -Fxq "$required" <<<"$images" || fail "fixed sandbox image missing: $required"
done

runner="$(service_block runner)"
worker="$(service_block worker)"

for compose_file in docker-compose.sandbox-test.yml docker-compose.worker-scale-test.yml; do
  rendered_runner="$(docker compose -f "$compose_file" config | awk '
    $0 == "  runner:" {inside = 1}
    inside && $0 ~ /^  [a-zA-Z0-9_-]+:$/ && $0 != "  runner:" {exit}
    inside {print}
  ')"
  grep -Fq '      - "4242"' <<<"$rendered_runner" \
    || fail "non-zero Docker socket GID was not rendered for $compose_file"
done

if grep -Fq 'DOCKER_SOCKET_GID:-0' \
    docker-compose.sandbox-test.yml docker-compose.worker-scale-test.yml; then
  fail "test Runner Compose must not fall back to Docker socket GID zero"
fi

grep -Fq '/var/run/docker.sock:/var/run/docker.sock' <<<"$runner" \
  || fail "Runner must receive the Docker socket"
grep -Fq 'RUNNER_SANDBOX_MODE: docker' <<<"$runner" \
  || fail "Runner must use the Docker sandbox executor"
grep -Fq 'RUNNER_MAX_CONCURRENT_JOBS: ${RUNNER_MAX_CONCURRENCY:-4}' <<<"$runner" \
  || fail "Runner concurrency must default to four"
grep -Fq '\"ok\":true,\"sandboxAvailable\":true' <<<"$runner" \
  || fail "Runner healthcheck must require ok and sandbox availability"

for compose_file in docker-compose.sandbox-test.yml docker-compose.worker-scale-test.yml; do
  grep -Fq '\"ok\":true,\"sandboxAvailable\":true' "$compose_file" \
    || fail "test Runner healthcheck must require ok and sandbox availability: $compose_file"
done

if grep -Fq '/var/run/docker.sock' <<<"$worker"; then
  fail "Worker must not receive the Docker socket"
fi
grep -Fq 'JUDGE_EXECUTION_MODE: remote' <<<"$worker" \
  || fail "Worker must use remote execution"
grep -Fq 'RUNNER_BASE_URL: http://runner:8080' <<<"$worker" \
  || fail "Worker must address the internal Runner service"

runtime_stage="$(awk '/^FROM .* AS runtime$/ {inside = 1} inside {print}' worker/Dockerfile)"
grep -Fq 'FROM eclipse-temurin:21-jre-jammy AS runtime' <<<"$runtime_stage" \
  || fail "Worker runtime must be JRE-only"
if grep -Eq 'python3|node-runtime|\bgcc\b|\bg\+\+\b|javac' <<<"$runtime_stage"; then
  fail "Worker runtime must not contain student language toolchains"
fi

ordinary_tests_line="$(grep -n 'Running frontend lint' scripts/test-docker.sh | cut -d: -f1)"
security_tests_line="$(grep -n 'Running real Docker sandbox security acceptance' scripts/test-docker.sh | cut -d: -f1)"
if [[ -z "$ordinary_tests_line" || -z "$security_tests_line" \
    || "$security_tests_line" -le "$ordinary_tests_line" ]]; then
  fail "Docker security acceptance must run after ordinary module tests"
fi

echo "Docker sandbox formal configuration checks: PASSED"
