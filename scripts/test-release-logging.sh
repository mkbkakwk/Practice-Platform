#!/usr/bin/env bash

set -euo pipefail

project_name="${COMPOSE_PROJECT_NAME:-practice-platform-test}"
compose_file="${COMPOSE_FILE:-docker-compose.test.yml}"
compose=(docker compose -p "$project_name" -f "$compose_file")
normal_services=(release-logging-backend release-logging-worker release-logging-runner release-logging-runner-production)
failure_services=(release-logging-backend-failure release-logging-worker-failure)
gate_services=("${normal_services[@]}" "${failure_services[@]}")
expected_git_sha="0123456789abcdef0123456789abcdef01234567"

cleanup_done=false

cleanup_gate_services() {
  local cleanup_rc=0
  local active_services
  local service

  echo "==> Cleaning release-logging gate-owned services"
  "${compose[@]}" rm --stop --force "${gate_services[@]}" || cleanup_rc=$?

  active_services="$("${compose[@]}" ps --status running --services 2>/dev/null || true)"
  for service in "${gate_services[@]}"; do
    if grep -Fxq "$service" <<<"$active_services"; then
      echo "RELEASE LOGGING TEST FAILED: gate-owned service remained active: $service" >&2
      cleanup_rc=1
    fi
  done

  cleanup_done=true
  return "$cleanup_rc"
}

on_exit() {
  local rc=$?
  if [[ "$cleanup_done" != true ]]; then
    if ! cleanup_gate_services; then
      echo "WARN: release-logging gate cleanup failed" >&2
      if [[ $rc -eq 0 ]]; then
        rc=1
      fi
    fi
  fi
  exit "$rc"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "RELEASE LOGGING TEST FAILED: $*" >&2
  "${compose[@]}" ps >&2 || true
  exit 1
}

json_log_for() {
  local service="$1"
  # Do not exit awk after the first matching record. With pipefail enabled,
  # that early close can make Docker Compose return a broken-pipe status on
  # Linux CI before it has finished writing the bounded log stream.
  "${compose[@]}" logs --no-color --no-log-prefix "$service" 2>/dev/null \
    | awk '!found && /^\{/ { print; found = 1 }'
}

assert_json_log() {
  local service="$1"
  local expected_service="$2"
  local log_line=""
  local attempt
  for attempt in $(seq 1 30); do
    log_line="$(json_log_for "$service")"
    if [[ -n "$log_line" ]]; then
      if printf '%s' "$log_line" | docker run --rm -i node:20-alpine node -e '
        let input = "";
        process.stdin.on("data", chunk => input += chunk);
        process.stdin.on("end", () => {
          const entry = JSON.parse(input);
          if (entry.service !== process.argv[1] || entry.gitSha !== process.argv[2] || !entry.level || !entry.message || !entry["@timestamp"]) process.exit(1);
        });
      ' "$expected_service" "$expected_git_sha"; then
        return 0
      fi
      fail "$service emitted malformed or incomplete JSON structured logging"
    fi
    sleep 1
  done
  fail "$service did not emit a structured JSON startup record within the bounded deadline"
}

assert_redacted_json_logs() {
  local service="$1"
  local scenario="$2"
  local expected_failure="$3"
  shift 3
  local attempt=0
  local result=""
  local node_exit=0

  while (( attempt < 30 )); do
    attempt=$((attempt + 1))
    set +e
    result="$("${compose[@]}" logs --no-color --no-log-prefix "$service" 2>/dev/null \
      | docker run --rm -i node:20-alpine node -e '
      let input = "";
      process.stdin.on("data", chunk => input += chunk);
      process.stdin.on("end", () => {
        const sentinels = [
          ["db-host", "db-internal.example"], ["db-name", "private_database"], ["db-user", "stage_db_user"],
          ["rabbit-host", "rabbit-internal.example"], ["rabbit-user", "stage_rabbit_user"],
          ["failure-password", "test-only-failure-password"], ["runner-token", "test-runner-token"],
          ["jwt-secret", "test-only-secret-not-for-production-0123456789"]
        ];
        const blocked = [
          ["jdbc-uri", /jdbc:postgresql:\/\//i],
          ["amqp-uri", /amqps?:\/\//i],
          ["connection-endpoint", /\[[A-Za-z0-9._-]+:\d{2,5}\]/],
          ["docker-socket", /\/var\/run\/docker\.sock/i],
          ["bearer-token", /\bBearer\s+[A-Za-z0-9._~+\/-]+=*/i]
        ];
        let validJson = 0;
        let leak = null;
        let diagnosableFailure = false;
        const safePath = path => path.replace(/[^A-Za-z0-9_.$\[\]]/g, "?");
        const findLeak = (value, path) => {
          if (typeof value === "string") {
            const sentinel = sentinels.find(([, candidate]) => value.includes(candidate));
            const blockedMatch = blocked.find(([, pattern]) => pattern.test(value));
            if (sentinel || blockedMatch) {
              return { category: sentinel?.[0] || blockedMatch?.[0], path: safePath(path) };
            }
            return null;
          }
          if (Array.isArray(value)) {
            for (let index = 0; index < value.length; index++) {
              const nested = findLeak(value[index], `${path}[${index}]`);
              if (nested) return nested;
            }
            return null;
          }
          if (value && typeof value === "object") {
            for (const [key, nestedValue] of Object.entries(value)) {
              const nested = findLeak(nestedValue, `${path}.${key}`);
              if (nested) return nested;
            }
          }
          return null;
        };
        let record = 0;
        for (const line of input.split(/\r?\n/)) {
          let entry;
          try { entry = JSON.parse(line); } catch { continue; }
          validJson++;
          record++;
          leak ||= (() => {
            const match = findLeak(entry, "$");
            return match && {
              ...match,
              record,
              logger: String(entry.logger_name || entry.logger || "unknown").replace(/[^A-Za-z0-9_.-]/g, "?"),
              level: String(entry.level || "unknown").replace(/[^A-Za-z]/g, "?")
            };
          })();
          if (entry.level && /^(WARN|ERROR)$/i.test(entry.level) && /(fail|unable|refused|exception|unavailable|connect)/i.test(entry.message || "")) diagnosableFailure = true;
        }
        if (validJson === 0) {
          console.error("check=json-log-missing jsonRecords=0");
          process.exit(2);
        }
        if (leak) {
          console.error(`check=sentinel-leak category=${leak.category} record=${leak.record} path=${leak.path} logger=${leak.logger} level=${leak.level} jsonRecords=${validJson}`);
          process.exit(3);
        }
        if (process.argv[1] === "true" && !diagnosableFailure) {
          console.error(`check=diagnostic-category-missing jsonRecords=${validJson}`);
          process.exit(4);
        }
        console.error(`check=pass jsonRecords=${validJson}`);
      });
    ' "$expected_failure" 2>&1)"
    node_exit=$?
    set -e

    if [[ $node_exit -eq 0 ]]; then
      return 0
    fi
    if [[ $node_exit -eq 3 ]]; then
      fail "service=$service scenario=$scenario $result"
    fi
    sleep 1
  done

  fail "service=$service scenario=$scenario ${result:-check=unknown}"
}

"${compose[@]}" up -d "${normal_services[@]}" || fail "could not start release-profile logging services"

assert_json_log release-logging-backend backend
assert_json_log release-logging-worker worker
assert_json_log release-logging-runner runner
assert_json_log release-logging-runner-production runner
assert_redacted_json_logs release-logging-backend normal false
assert_redacted_json_logs release-logging-worker normal false
assert_redacted_json_logs release-logging-runner normal false
assert_redacted_json_logs release-logging-runner-production production false

"${compose[@]}" up -d "${failure_services[@]}" || fail "could not start isolated release-profile failure services"

failure_ready=false
for attempt in $(seq 1 30); do
  if "${compose[@]}" logs --no-color --no-log-prefix "${failure_services[@]}" 2>/dev/null \
    | grep -Eq '^\{'; then
    failure_ready=true
    break
  fi
  sleep 1
done
[[ "$failure_ready" == true ]] || fail "isolated failure services did not emit JSON logs within the bounded deadline"
assert_redacted_json_logs release-logging-backend-failure db-failure true
assert_redacted_json_logs release-logging-worker-failure rabbit-failure true

if "${compose[@]}" logs --no-color "${normal_services[@]}" "${failure_services[@]}" \
  | grep -Eq 'LogstashConsoleAppender|ClassNotFoundException|DynamicClassLoadingException|Could not create appender|logback configuration error'; then
  fail "release-profile logging startup emitted a Logback initialization error"
fi

if ! cleanup_gate_services; then
  fail "gate-owned application services remained active after logging checks"
fi

echo "Release logging profile checks passed (JSON, normal-path redaction, and isolated failure-path redaction)."
