#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1

project_name="${WORKER_SCALE_TEST_PROJECT:-practice-platform-worker-scale-test}"
compose=(docker compose -p "$project_name" -f docker-compose.worker-scale-test.yml)
runner_instance="worker-scale-runner"
socket_gid_configured=false

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-socket-gid.sh"

print_worker_scale_diagnostics() {
  print_docker_socket_diagnostics
  "${compose[@]}" logs --no-color runner worker >&2 || true
}

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

configure_docker_socket_gid
socket_gid_configured=true

echo "==> Building fixed sandbox images for the disposable Runner"
docker compose -p practice-platform-worker-scale-images \
  -f docker-compose.sandbox-test.yml --profile images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image

echo "==> Starting three competing Worker consumers"
if ! "${compose[@]}" up -d --build --wait --scale worker=3; then
  echo "ERROR: Worker scale services did not become healthy" >&2
  print_worker_scale_diagnostics
  exit 1
fi

mapfile -t worker_ids < <("${compose[@]}" ps -q worker)
if [[ ${#worker_ids[@]} -ne 3 ]]; then
  echo "ERROR: expected exactly three Worker containers, got ${#worker_ids[@]}" >&2
  print_worker_scale_diagnostics
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
  print_worker_scale_diagnostics
  exit 1
fi

"${compose[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U scale_test -d scale_test <<'SQL'
INSERT INTO "User" (id, username, password) VALUES (1, 'scale-test-user', 'not-a-real-password');
INSERT INTO "Problem" (id, slug, title, description, time_limit, memory_limit, test_cases)
VALUES (1, 'scale-test', 'Scale test', 'Disposable test problem', 1000, 128,
        '[{"input":"","output":"1"}]');
INSERT INTO "Submission" (id, user_id, problem_id, language, code)
VALUES (1, 1, 1, 'python', 'print(1)');
SQL

event_id='11111111-1111-1111-1111-111111111111'
payload="{\"eventId\":\"$event_id\",\"submissionId\":1,\"schemaVersion\":1,\"deliveryAttempt\":0}"
for _ in 1 2 3 4 5; do
  "${compose[@]}" exec -T rabbitmq rabbitmqadmin \
    -u scale_test -p scale_test_password publish \
    exchange=oj.judge routing_key=oj.judge.submit \
    payload="$payload" \
    properties="{\"content_type\":\"application/json\",\"delivery_mode\":2,\"message_id\":\"$event_id\",\"correlation_id\":\"1\"}" >/dev/null
done

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
  print_worker_scale_diagnostics
  exit 1
fi

logs=$("${compose[@]}" logs --no-color worker)
judging_count=$(grep -c 'Judge claim success eventId=.* submissionId=1 ' <<<"$logs" || true)
result_count=$(grep -c 'Judge result committed eventId=.* submissionId=1 .* verdict=AC' <<<"$logs" || true)
if [[ "$judging_count" -ne 1 || "$result_count" -ne 1 ]]; then
  echo "ERROR: expected exactly one judge and one AC result log; judging=$judging_count result=$result_count" >&2
  print_worker_scale_diagnostics
  exit 1
fi

judge_attempts=$("${compose[@]}" exec -T db psql -At -U scale_test -d scale_test \
  -c 'SELECT judge_attempt_count FROM "Submission" WHERE id = 1;')
if [[ "$judge_attempts" != "1" ]]; then
  echo "ERROR: expected one database judge claim, got $judge_attempts" >&2
  print_worker_scale_diagnostics
  exit 1
fi

queue_state=""
while read -r queue_name ready unacked consumers; do
  [[ "$queue_name" == "oj.judge.queue" ]] && queue_state="$ready $unacked $consumers"
done < <("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q \
  name messages_ready messages_unacknowledged consumers)
if [[ "$queue_state" != "0 0 3" ]]; then
  echo "ERROR: unexpected queue state: $queue_state" >&2
  print_worker_scale_diagnostics
  exit 1
fi

for queue in oj.judge.retry.queue oj.judge.dlq; do
  queued=""
  while read -r queue_name ready unacked; do
    [[ "$queue_name" == "$queue" ]] && queued="$ready $unacked"
  done < <("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q \
    name messages_ready messages_unacknowledged)
  if [[ "$queued" != "0 0" ]]; then
    echo "ERROR: queue $queue did not drain: $queued" >&2
    print_worker_scale_diagnostics
    exit 1
  fi
done

if docker container ls -aq --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  echo "ERROR: residual student container after Worker scale test" >&2
  exit 1
fi
if docker volume ls -q --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  echo "ERROR: residual student volume after Worker scale test" >&2
  exit 1
fi

echo "Worker scale test: PASSED (3 consumers, 5 duplicate deliveries, 1 effective judge, queues drained)"
