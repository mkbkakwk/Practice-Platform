#!/usr/bin/env bash

set -euo pipefail
export COMPOSE_DISABLE_ENV_FILE=1
export RUNNER_INSTANCE_ID=contest-core-runner

project_name="${CONTEST_CORE_TEST_PROJECT:-practice-platform-contest-core-test}"
compose=(docker compose -p "$project_name" -f docker-compose.judge-reliability-test.yml)
runner_instance="$RUNNER_INSTANCE_ID"
socket_gid_configured=false

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-socket-gid.sh"

diagnostics() {
  print_docker_socket_diagnostics
  "${compose[@]}" ps -a >&2 || true
  "${compose[@]}" logs --no-color --tail=120 backend runner worker >&2 || true
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
    echo "ERROR: Contest core cleanup failed" >&2
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

json_number() {
  local key="$1"
  sed -n "s/.*\"$key\":\([0-9][0-9]*\).*/\1/p"
}

register() {
  local username="$1"
  "${compose[@]}" exec -T backend curl -fsS \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"password\":\"contest-test-password\"}" \
    http://127.0.0.1:4000/api/auth/register
}

login_token() {
  local username="$1" response
  response=$("${compose[@]}" exec -T backend curl -fsS \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"password\":\"contest-test-password\"}" \
    http://127.0.0.1:4000/api/auth/login)
  sed -n 's/.*"token":"\([^"]*\)".*/\1/p' <<<"$response"
}

api_post() {
  local token="$1" path="$2" body="$3"
  "${compose[@]}" exec -T backend curl -fsS \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    --data "$body" "http://127.0.0.1:4000/api$path"
}

psql_value() {
  "${compose[@]}" exec -T db psql -At -U reliability_test -d reliability_test -c "$1"
}

wait_verdict() {
  local submission_id="$1" expected="$2" deadline=$((SECONDS + 60)) verdict=""
  while (( SECONDS < deadline )); do
    verdict=$(psql_value "SELECT verdict FROM \"Submission\" WHERE id=$submission_id")
    [[ "$verdict" == "$expected" ]] && return 0
    sleep 1
  done
  echo "$verdict"
  return 1
}

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Engine with Compose v2 is required" >&2
  exit 127
fi
configure_docker_socket_gid
socket_gid_configured=true

echo "==> Building fixed sandbox images for Contest submissions"
docker compose -p practice-platform-contest-core-images \
  -f docker-compose.sandbox-test.yml --profile images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image

echo "==> Starting Contest acceptance services with three Workers"
"${compose[@]}" up -d --build --wait db rabbitmq backend runner || fail "Contest services did not become healthy"
"${compose[@]}" up -d --build --wait --scale worker=3 worker || fail "three Contest Workers did not start"
mapfile -t worker_ids < <("${compose[@]}" ps -q worker)
[[ ${#worker_ids[@]} -eq 3 ]] || fail "expected exactly three Worker containers"

teacher_registration=$(register contest_teacher)
teacher_id=$(json_number id <<<"$teacher_registration")
[[ "$teacher_id" =~ ^[0-9]+$ ]] || fail "teacher registration did not return an id"
"${compose[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U reliability_test -d reliability_test \
  -c "UPDATE \"User\" SET role='TEACHER' WHERE id=$teacher_id" >/dev/null
teacher_token=$(login_token contest_teacher)
[[ -n "$teacher_token" ]] || fail "teacher login did not return a token"

student_registration=$(register contest_student)
student_token=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' <<<"$student_registration")
[[ -n "$student_token" ]] || fail "student registration did not return a token"

problem_response=$(api_post "$teacher_token" /problems \
  '{"slug":"contest-core-integration","title":"Contest Core Integration","description":"Disposable Contest acceptance problem","difficulty":"EASY","timeLimit":2000,"memoryLimit":128,"tags":["contest"],"samples":[{"input":"","output":"1"}],"testCases":[{"input":"","output":"1"}],"visible":true,"contentVisibility":"CONTEST_ONLY"}')
problem_id=$(json_number id <<<"$problem_response")
[[ "$problem_id" =~ ^[0-9]+$ ]] || fail "problem creation did not return an id"

start_epoch=$(( $(date -u +%s) + 12 ))
end_epoch=$(( start_epoch + 180 ))
start_at=$(date -u -d "@$start_epoch" +%Y-%m-%dT%H:%M:%SZ)
end_at=$(date -u -d "@$end_epoch" +%Y-%m-%dT%H:%M:%SZ)
contest_response=$(api_post "$teacher_token" /contests \
  "{\"title\":\"Docker Contest Acceptance\",\"description\":\"Contest API to Docker Runner\",\"startAt\":\"$start_at\",\"endAt\":\"$end_at\",\"accessType\":\"OPEN\"}")
contest_id=$(json_number id <<<"$contest_response")
[[ "$contest_id" =~ ^[0-9]+$ ]] || fail "contest creation did not return an id"

problem_link=$(api_post "$teacher_token" "/contests/$contest_id/problems" \
  "{\"problemType\":\"ALGORITHM\",\"problemId\":$problem_id,\"label\":\"A\"}")
contest_problem_id=$(json_number contestProblemId <<<"$problem_link")
[[ "$contest_problem_id" =~ ^[0-9]+$ ]] || fail "contest problem creation did not return an id"
api_post "$teacher_token" "/contests/$contest_id/publish" '{}' >/dev/null
api_post "$student_token" "/contests/$contest_id/join" '{}' >/dev/null

echo "==> Waiting for the server-derived RUNNING phase"
deadline=$((SECONDS + 30))
phase=""
while (( SECONDS < deadline )); do
  detail=$("${compose[@]}" exec -T backend curl -fsS \
    -H "Authorization: Bearer $student_token" \
    "http://127.0.0.1:4000/api/contests/$contest_id")
  phase=$(sed -n 's/.*"phase":"\([A-Z_]*\)".*/\1/p' <<<"$detail")
  [[ "$phase" == "RUNNING" ]] && break
  sleep 1
done
[[ "$phase" == "RUNNING" ]] || fail "Contest did not enter RUNNING; phase=$phase"
grep -Fq 'Disposable Contest acceptance problem' <<<"$detail" \
  || fail "participant could not see CONTEST_ONLY problem after start"

echo "==> Submitting AC, WA, and CE through Contest endpoints"
ac_response=$(api_post "$student_token" "/contests/$contest_id/problems/$contest_problem_id/submissions" \
  '{"language":"python","code":"print(1)"}')
wa_response=$(api_post "$student_token" "/contests/$contest_id/problems/$contest_problem_id/submissions" \
  '{"language":"python","code":"print(2)"}')
ce_response=$(api_post "$student_token" "/contests/$contest_id/problems/$contest_problem_id/submissions" \
  '{"language":"c","code":"int main( {"}')
ac_id=$(json_number submissionId <<<"$ac_response")
wa_id=$(json_number submissionId <<<"$wa_response")
ce_id=$(json_number submissionId <<<"$ce_response")
[[ "$ac_id" =~ ^[0-9]+$ && "$wa_id" =~ ^[0-9]+$ && "$ce_id" =~ ^[0-9]+$ ]] \
  || fail "Contest submissions did not return ids"
wait_verdict "$ac_id" AC >/dev/null || fail "Contest AC submission did not reach AC"
wait_verdict "$wa_id" WA >/dev/null || fail "Contest WA submission did not reach WA"
wait_verdict "$ce_id" CE >/dev/null || fail "Contest CE submission did not reach CE"

context_rows=$(psql_value "SELECT COUNT(*) FROM \"Submission\" WHERE id IN ($ac_id,$wa_id,$ce_id) AND contest_problem_id=$contest_problem_id")
[[ "$context_rows" == "3" ]] || fail "Contest submission context was not persisted atomically"
published_outbox=$(psql_value "SELECT COUNT(*) FROM judge_outbox WHERE submission_id IN ($ac_id,$wa_id,$ce_id) AND status='PUBLISHED'")
[[ "$published_outbox" == "3" ]] || fail "Contest outbox events were not all published"

queue_state=""
deadline=$((SECONDS + 20))
while (( SECONDS < deadline )); do
  queue_state=$("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues -q \
    name messages_ready messages_unacknowledged | while read -r queue_name ready unacknowledged; do
      if [[ "$queue_name" == "reliability.judge.queue" ]]; then
        printf '%s %s\n' "$ready" "$unacknowledged"
        break
      fi
    done)
  [[ "$queue_state" == "0 0" ]] && break
  sleep 1
done
[[ "$queue_state" == "0 0" ]] || fail "Contest judge queue did not drain: $queue_state"

if docker container ls -aq --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  fail "residual student container after Contest acceptance"
fi
if docker volume ls -q --filter "label=com.practice-platform.runner-instance=$runner_instance" | grep -q .; then
  fail "residual student volume after Contest acceptance"
fi

echo "Contest core integration: PASSED (Contest API, CONTEST_ONLY visibility, Outbox, 3 Workers, Docker Runner, AC/WA/CE)"
