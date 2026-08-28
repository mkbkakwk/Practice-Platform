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
runner_rc=125
worker_runner_contract_rc=125
frontend_rc=125
readiness_timeout_rc=125
release_config_rc=125
formal_sandbox_config_rc=125
docker_sandbox_security_rc=125
worker_scale_rc=125
judge_reliability_rc=125
contest_core_rc=125

echo "==> Building isolated test images"
"${compose[@]}" build || build_rc=$?

if [[ $build_rc -eq 0 ]]; then
  echo "==> Validating immutable release configuration"
  release_config_rc=0
  "${compose[@]}" run --rm --no-deps release-config-test || release_config_rc=$?

  echo "==> Validating formal Docker sandbox configuration"
  formal_sandbox_config_rc=0
  bash ./scripts/test-docker-sandbox-config.sh || formal_sandbox_config_rc=$?
fi

if [[ $build_rc -eq 0 && $release_config_rc -eq 0 && $formal_sandbox_config_rc -eq 0 ]]; then
  echo "==> Starting isolated PostgreSQL, RabbitMQ, and Runner contract service"
  "${compose[@]}" up -d --wait test-db test-rabbitmq runner-contract || startup_rc=$?
fi

if [[ $build_rc -eq 0 && $release_config_rc -eq 0 && $startup_rc -eq 0 ]]; then
  echo "==> Running Runner service tests"
  runner_rc=0
  "${compose[@]}" run --rm runner-test || runner_rc=$?

  echo "==> Running backend tests"
  backend_rc=0
  "${compose[@]}" run --rm backend-test || backend_rc=$?

  echo "==> Running worker tests"
  worker_rc=0
  "${compose[@]}" run --rm worker-test || worker_rc=$?

  echo "==> Running Worker-to-Runner HTTP contract test"
  worker_runner_contract_rc=0
  "${compose[@]}" run --rm worker-runner-contract-test || worker_runner_contract_rc=$?

  echo "==> Running frontend lint, authentication tests, and build"
  frontend_rc=0
  "${compose[@]}" run --rm frontend-test || frontend_rc=$?
fi

if [[ $build_rc -eq 0 && $release_config_rc -eq 0 && $formal_sandbox_config_rc -eq 0 \
    && $startup_rc -eq 0 && $backend_rc -eq 0 && $worker_rc -eq 0 \
    && $runner_rc -eq 0 && $worker_runner_contract_rc -eq 0 && $frontend_rc -eq 0 ]]; then
  echo "==> Running bounded readiness dependency-outage acceptance"
  readiness_timeout_rc=0
  bash ./scripts/test-readiness-timeout.sh || readiness_timeout_rc=$?

fi

if [[ $build_rc -eq 0 && $release_config_rc -eq 0 && $formal_sandbox_config_rc -eq 0 \
    && $startup_rc -eq 0 && $backend_rc -eq 0 && $worker_rc -eq 0 \
    && $runner_rc -eq 0 && $worker_runner_contract_rc -eq 0 && $frontend_rc -eq 0 \
    && $readiness_timeout_rc -eq 0 ]]; then
  echo "==> Running real Docker sandbox security acceptance"
  docker_sandbox_security_rc=0
  bash ./scripts/test-docker-sandbox.sh || docker_sandbox_security_rc=$?

  if [[ $docker_sandbox_security_rc -eq 0 ]]; then
    echo "==> Running three-Worker competing-consumer acceptance"
    worker_scale_rc=0
    bash ./scripts/test-worker-scale.sh || worker_scale_rc=$?

    if [[ $worker_scale_rc -eq 0 ]]; then
      echo "==> Running judge message reliability fault injection"
      judge_reliability_rc=0
      bash ./scripts/test-judge-reliability.sh || judge_reliability_rc=$?

      if [[ $judge_reliability_rc -eq 0 ]]; then
        echo "==> Running Contest API-to-Docker-Runner acceptance"
        contest_core_rc=0
        bash ./scripts/test-contest-core.sh || contest_core_rc=$?
      fi
    fi
  fi
fi

echo
echo "Docker test summary"
printf '  image build:   %s\n' "$build_rc"
printf '  dependencies:  %s\n' "$startup_rc"
printf '  backend-test:  %s\n' "$backend_rc"
printf '  worker-test:   %s\n' "$worker_rc"
printf '  runner-test:   %s\n' "$runner_rc"
printf '  worker-runner-contract: %s\n' "$worker_runner_contract_rc"
printf '  frontend-test: %s\n' "$frontend_rc"
printf '  readiness-timeout: %s\n' "$readiness_timeout_rc"
printf '  release-config: %s\n' "$release_config_rc"
printf '  formal-sandbox-config: %s\n' "$formal_sandbox_config_rc"
printf '  docker-sandbox-security: %s\n' "$docker_sandbox_security_rc"
printf '  worker-scale: %s\n' "$worker_scale_rc"
printf '  judge-reliability: %s\n' "$judge_reliability_rc"
printf '  contest-core: %s\n' "$contest_core_rc"

for rc in "$build_rc" "$release_config_rc" "$startup_rc" "$backend_rc" "$worker_rc" \
  "$runner_rc" "$worker_runner_contract_rc" "$frontend_rc" "$formal_sandbox_config_rc" \
  "$readiness_timeout_rc" "$docker_sandbox_security_rc" "$worker_scale_rc"; do
  if [[ $rc -ne 0 ]]; then
    exit "$rc"
  fi
done

if [[ $judge_reliability_rc -ne 0 ]]; then
  exit "$judge_reliability_rc"
fi

if [[ $contest_core_rc -ne 0 ]]; then
  exit "$contest_core_rc"
fi

exit 0
