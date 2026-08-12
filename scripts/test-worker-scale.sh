#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1

project_name="${WORKER_SCALE_TEST_PROJECT:-practice-platform-worker-scale-test}"
compose=(docker compose -p "$project_name" -f docker-compose.worker-scale-test.yml)
runner_instance="worker-scale-runner"

cleanup() {
  local original_rc=$?
  local cleanup_rc=0
  trap - EXIT INT TERM
  "${compose[@]}" down --remove-orphans || cleanup_rc=$?

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
    echo "ERROR: Worker scale test cleanup failed" >&2
    exit "$cleanup_rc"
  fi
  exit "$original_rc"
}
trap cleanup EXIT INT TERM

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Engine with Compose v2 is required" >&2
  exit 127
fi

echo "==> Building fixed sandbox images for the disposable Runner"
docker compose -p practice-platform-worker-scale-images \
  -f docker-compose.sandbox-test.yml --profile images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image

echo "==> Starting three competing Worker consumers"
"${compose[@]}" up -d --build --wait --scale worker=3

mapfile -t worker_ids < <("${compose[@]}" ps -q worker)
if [[ ${#worker_ids[@]} -ne 3 ]]; then
  echo "ERROR: expected exactly three Worker containers, got ${#worker_ids[@]}" >&2
  exit 1
fi

deadline=$((SECONDS + 45))
queue_declared=false
while (( SECONDS < deadline )); do
  if "${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q name \
      | grep -Fxq 'oj.judge.queue'; then
    queue_declared=true
    break
  fi
  sleep 1
done
if [[ "$queue_declared" != "true" ]]; then
  echo "ERROR: Workers did not declare the judge queue" >&2
  "${compose[@]}" logs --no-color worker >&2
  exit 1
fi

"${compose[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U scale_test -d scale_test <<'SQL'
INSERT INTO "User" (id, username, password) VALUES (1, 'scale-test-user', 'not-a-real-password');
INSERT INTO "Problem" (id, slug, title, description) VALUES (1, 'scale-test', 'Scale test', 'Disposable test problem');
INSERT INTO "Submission" (id, user_id, problem_id, language, code)
VALUES (1, 1, 1, 'python', 'print(1)');
SQL

payload='{"submissionId":1,"language":"python","code":"print(1)","timeLimitMs":1000,"memoryLimitKb":131072,"testCasesJson":"[{\"input\":\"\",\"output\":\"1\"}]"}'
"${compose[@]}" exec -T rabbitmq rabbitmqadmin \
  -u scale_test -p scale_test_password publish \
  exchange=oj.judge routing_key=oj.judge.submit \
  payload="$payload" properties='{"content_type":"application/json"}' >/dev/null

deadline=$((SECONDS + 45))
verdict=""
while (( SECONDS < deadline )); do
  verdict=$("${compose[@]}" exec -T db psql -At -U scale_test -d scale_test \
    -c 'SELECT verdict FROM "Submission" WHERE id = 1;')
  [[ "$verdict" == "AC" ]] && break
  sleep 1
done
if [[ "$verdict" != "AC" ]]; then
  echo "ERROR: submission did not reach AC; final verdict=$verdict" >&2
  "${compose[@]}" logs --no-color worker >&2
  exit 1
fi

logs=$("${compose[@]}" logs --no-color worker)
judging_count=$(grep -c '\[worker\] judging submission #1 ' <<<"$logs" || true)
result_count=$(grep -c '\[worker\] submission #1 requestId=.* verdict=AC ' <<<"$logs" || true)
if [[ "$judging_count" -ne 1 || "$result_count" -ne 1 ]]; then
  echo "ERROR: expected exactly one judge and one AC result log; judging=$judging_count result=$result_count" >&2
  exit 1
fi

queue_state=$("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q \
  name messages_ready messages_unacknowledged consumers | awk '$1 == "oj.judge.queue" {print $2 " " $3 " " $4}')
if [[ "$queue_state" != "0 0 3" ]]; then
  echo "ERROR: unexpected queue state: $queue_state" >&2
  exit 1
fi

if docker container ls -aq --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  echo "ERROR: residual student container after Worker scale test" >&2
  exit 1
fi
if docker volume ls -q --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  echo "ERROR: residual student volume after Worker scale test" >&2
  exit 1
fi

echo "Worker scale test: PASSED (3 consumers, exactly-once observed result, queue drained)"
