#!/usr/bin/env bash

set -euo pipefail

project_name="${COMPOSE_PROJECT_NAME:-practice-platform-test}"
compose_file="${COMPOSE_FILE:-docker-compose.test.yml}"
compose=(docker compose -p "$project_name" -f "$compose_file")
services=(release-logging-backend release-logging-worker release-logging-runner release-logging-runner-production)
expected_git_sha="0123456789abcdef0123456789abcdef01234567"

fail() {
  echo "RELEASE LOGGING TEST FAILED: $*" >&2
  "${compose[@]}" ps >&2 || true
  "${compose[@]}" logs --no-color --tail 120 "${services[@]}" >&2 || true
  exit 1
}

json_log_for() {
  local service="$1"
  "${compose[@]}" logs --no-color --no-log-prefix "$service" 2>/dev/null \
    | awk '/^\{/{print; exit}'
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

"${compose[@]}" up -d "${services[@]}" || fail "could not start release-profile logging services"

assert_json_log release-logging-backend backend
assert_json_log release-logging-worker worker
assert_json_log release-logging-runner runner
assert_json_log release-logging-runner-production runner

if "${compose[@]}" logs --no-color "${services[@]}" \
  | grep -Eq 'LogstashConsoleAppender|ClassNotFoundException|DynamicClassLoadingException|Could not create appender|logback configuration error'; then
  fail "release-profile logging startup emitted a Logback initialization error"
fi

echo "Release logging profile checks passed (Backend, Worker, Runner staging and Runner production JSON startup)."
