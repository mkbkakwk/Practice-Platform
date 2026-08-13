#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1

project_name="${JUDGE_RELIABILITY_TEST_PROJECT:-practice-platform-judge-reliability-test}"
compose=(docker compose -p "$project_name" -f docker-compose.judge-reliability-test.yml)
runner_instance="reliability-runner"
socket_gid_configured=false

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-socket-gid.sh"

diagnostics() {
  print_docker_socket_diagnostics
  "${compose[@]}" ps -a >&2 || true
  "${compose[@]}" logs --no-color --tail=120 backend runner worker >&2 || true
  "${compose[@]}" logs --no-color --tail=30 rabbitmq >&2 || true
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
    echo "ERROR: Judge reliability cleanup failed" >&2
    exit "$cleanup_rc"
  fi
  exit "$original_rc"
}
trap cleanup EXIT INT TERM

fail() {
  echo "ERROR: $*" >&2
  diagnostics
  exit 1
}

psql_value() {
  "${compose[@]}" exec -T db psql -At -U reliability_test -d reliability_test -c "$1"
}

wait_for_value() {
  local query="$1" expected="$2" timeout_seconds="$3" value=""
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    value="$(psql_value "$query")"
    [[ "$value" == "$expected" ]] && return 0
    sleep 1
  done
  echo "$value"
  return 1
}

register_and_submit() {
  local username="$1" code="$2" registration token body response
  registration=$("${compose[@]}" exec -T backend curl -fsS \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"password\":\"reliability-password\"}" \
    http://127.0.0.1:4000/api/auth/register)
  token=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' <<<"$registration")
  [[ -n "$token" ]] || fail "registration did not return a token for $username"
  body="{\"problemId\":1,\"language\":\"python\",\"code\":\"$code\"}"
  response=$("${compose[@]}" exec -T backend curl -fsS \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    --data "$body" http://127.0.0.1:4000/api/submissions)
  sed -n 's/.*"submissionId":\([0-9][0-9]*\).*/\1/p' <<<"$response"
}

publish_duplicate() {
  local event_id="$1" submission_id="$2"
  local payload="{\"eventId\":\"$event_id\",\"submissionId\":$submission_id,\"schemaVersion\":1,\"deliveryAttempt\":0}"
  "${compose[@]}" exec -T rabbitmq rabbitmqadmin \
    -u reliability_test -p reliability_test_password publish \
    exchange=reliability.judge routing_key=reliability.judge.submit \
    payload="$payload" \
    properties="{\"content_type\":\"application/json\",\"delivery_mode\":2,\"message_id\":\"$event_id\",\"correlation_id\":\"$submission_id\"}" >/dev/null
}

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Engine with Compose v2 is required" >&2
  exit 127
fi
configure_docker_socket_gid
socket_gid_configured=true

echo "==> Building fixed sandbox images"
docker compose -p practice-platform-reliability-images \
  -f docker-compose.sandbox-test.yml --profile images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image

echo "==> Starting PostgreSQL and Backend with RabbitMQ intentionally absent"
"${compose[@]}" up -d --build --wait db backend || fail "Backend did not start without RabbitMQ"

"${compose[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U reliability_test -d reliability_test <<'SQL'
INSERT INTO "Problem" (id, slug, title, description, time_limit, memory_limit, test_cases)
VALUES (1, 'reliability-test', 'Reliability test', 'Disposable fault-injection problem',
        10000, 128, '[{"input":"","output":"1"}]');
SQL

outage_submission=$(register_and_submit outage_user 'print(1)')
[[ "$outage_submission" =~ ^[0-9]+$ ]] || fail "Rabbit outage submission was not accepted"
outage_state=$(psql_value "SELECT s.verdict || ' ' || o.status FROM \"Submission\" s JOIN judge_outbox o ON o.submission_id=s.id WHERE s.id=$outage_submission")
[[ "$outage_state" == "PENDING PENDING" || "$outage_state" == "PENDING PUBLISHING" ]] \
  || fail "submission/outbox was not durably pending during Rabbit outage: $outage_state"

echo "==> Restoring RabbitMQ and verifying outbox recovery"
"${compose[@]}" up -d --wait rabbitmq || fail "RabbitMQ recovery failed"
wait_for_value "SELECT status FROM judge_outbox WHERE submission_id=$outage_submission" PUBLISHED 30 >/dev/null \
  || fail "outbox did not publish after RabbitMQ recovery"

echo "==> Starting Runner and three Worker replicas"
"${compose[@]}" up -d --build --wait runner || fail "Runner did not become sandbox-available"
"${compose[@]}" up -d --build --wait --scale worker=3 worker || fail "three Workers did not start"
mapfile -t worker_ids < <("${compose[@]}" ps -q worker)
[[ ${#worker_ids[@]} -eq 3 ]] || fail "expected three Worker containers"
wait_for_value "SELECT verdict FROM \"Submission\" WHERE id=$outage_submission" AC 45 >/dev/null \
  || fail "Rabbit outage submission did not recover to AC"

echo "==> Publishing five duplicates and proving one effective judge"
event_id=$(psql_value "SELECT event_id FROM judge_outbox WHERE submission_id=$outage_submission")
for _ in 1 2 3 4 5; do publish_duplicate "$event_id" "$outage_submission"; done
sleep 3
attempts=$(psql_value "SELECT judge_attempt_count FROM \"Submission\" WHERE id=$outage_submission")
[[ "$attempts" == "1" ]] || fail "duplicate deliveries caused $attempts effective claims"
claim_logs=$("${compose[@]}" logs --no-color worker | \
  grep -c "Judge claim success eventId=$event_id submissionId=$outage_submission " || true)
[[ "$claim_logs" == "1" ]] || fail "expected one effective judge log, got $claim_logs"

echo "==> Injecting a temporary Runner outage"
"${compose[@]}" stop runner >/dev/null
temporary_submission=$(register_and_submit temp_runner 'print(1)')
deadline=$((SECONDS + 20))
temporary_attempts=0
while (( SECONDS < deadline )); do
  temporary_attempts=$(psql_value "SELECT judge_attempt_count FROM \"Submission\" WHERE id=$temporary_submission")
  [[ "$temporary_attempts" -ge 1 ]] && break
  sleep 1
done
[[ "$temporary_attempts" -ge 1 ]] || fail "temporary Runner outage did not produce a retryable attempt"
"${compose[@]}" up -d --wait runner || fail "Runner did not recover"
wait_for_value "SELECT verdict FROM \"Submission\" WHERE id=$temporary_submission" AC 45 >/dev/null \
  || fail "temporary Runner outage did not recover to AC"

echo "==> Killing the Worker that owns an in-flight judge and verifying lease recovery"
crash_submission=$(register_and_submit crash_worker 'import time\ntime.sleep(5)\nprint(1)')
wait_for_value "SELECT verdict FROM \"Submission\" WHERE id=$crash_submission" JUDGING 20 >/dev/null \
  || fail "crash-recovery submission was not claimed"
owner=""
deadline=$((SECONDS + 10))
while (( SECONDS < deadline )) && [[ -z "$owner" ]]; do
  for worker_id in "${worker_ids[@]}"; do
    if docker logs "$worker_id" 2>&1 | grep -q "Judge claim success .* submissionId=$crash_submission "; then
      owner="$worker_id"
      break
    fi
  done
  [[ -n "$owner" ]] || sleep 1
done
[[ -n "$owner" ]] || fail "could not identify the Worker owning submission $crash_submission"
docker kill "$owner" >/dev/null || fail "could not kill the Worker owning submission $crash_submission"
wait_for_value "SELECT verdict FROM \"Submission\" WHERE id=$crash_submission" AC 60 >/dev/null \
  || fail "another Worker did not recover the expired judge lease"
crash_attempts=$(psql_value "SELECT judge_attempt_count FROM \"Submission\" WHERE id=$crash_submission")
[[ "$crash_attempts" == "2" ]] || fail "expected two crash-recovery attempts, got $crash_attempts"
"${compose[@]}" up -d --no-build --wait --scale worker=3 worker >/dev/null \
  || fail "Worker replica restoration failed"

echo "==> Injecting a permanent Runner outage and verifying bounded retry plus DLQ"
"${compose[@]}" stop runner >/dev/null
failed_submission=$(register_and_submit perm_runner 'print(1)')
wait_for_value "SELECT verdict FROM \"Submission\" WHERE id=$failed_submission" JUDGE_FAILED 45 >/dev/null \
  || fail "permanent Runner outage did not reach JUDGE_FAILED"
failed_attempts=$(psql_value "SELECT judge_attempt_count FROM \"Submission\" WHERE id=$failed_submission")
[[ "$failed_attempts" == "3" ]] || fail "expected exactly three bounded attempts, got $failed_attempts"
dlq_ready=""
while read -r queue_name ready; do
  [[ "$queue_name" == "reliability.judge.dlq" ]] && dlq_ready="$ready"
done < <("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q name messages_ready)
[[ "$dlq_ready" == "1" ]] || fail "expected one final dead letter, got ${dlq_ready:-missing}"

remaining_outbox=$(psql_value "SELECT COUNT(*) FROM judge_outbox WHERE status <> 'PUBLISHED'")
[[ "$remaining_outbox" == "0" ]] || fail "$remaining_outbox outbox events remained unpublished"
ready_total=0
unacked_total=0
while read -r queue_name ready unacked; do
  if [[ "$queue_name" == "reliability.judge.queue" || "$queue_name" == "reliability.judge.retry.queue" ]]; then
    ready_total=$((ready_total + ready))
    unacked_total=$((unacked_total + unacked))
  fi
done < <("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q \
  name messages_ready messages_unacknowledged)
queue_state="$ready_total $unacked_total"
[[ "$queue_state" == "0 0" ]] || fail "judge/retry queues did not drain: $queue_state"

if docker container ls -aq --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  fail "residual student container after reliability fault injection"
fi
if docker volume ls -q --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  fail "residual student volume after reliability fault injection"
fi

echo "Judge reliability test: PASSED (Rabbit outage recovery, duplicate idempotency, Worker crash recovery, bounded Runner retry, DLQ)"
