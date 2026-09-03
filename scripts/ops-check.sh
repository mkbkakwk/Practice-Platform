#!/usr/bin/env bash

# Read-only operational evidence for a named environment.  It intentionally
# has no restart, backup, retention, queue, or database mutation operation.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/backup-lib.sh"

usage() {
  echo "usage: ops-check.sh --environment staging --expected-sha FULL_SHA --backup-root DIR [--base-url URL] [--project NAME] [--max-backup-age-seconds N] [--minimum-free-bytes N]" >&2
  exit 2
}

environment=""
expected_sha=""
backup_root=""
base_url="${OPS_BASE_URL:-http://localhost:18080}"
project="${OPS_PROJECT:-practice-platform-staging}"
max_backup_age="${OPS_MAX_BACKUP_AGE_SECONDS:-93600}"
minimum_free="${BACKUP_MIN_FREE_BYTES:-1073741824}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) environment="${2:-}"; shift 2;;
    --expected-sha) expected_sha="${2:-}"; shift 2;;
    --backup-root) backup_root="${2:-}"; shift 2;;
    --base-url) base_url="${2:-}"; shift 2;;
    --project) project="${2:-}"; shift 2;;
    --max-backup-age-seconds) max_backup_age="${2:-}"; shift 2;;
    --minimum-free-bytes) minimum_free="${2:-}"; shift 2;;
    *) usage;;
  esac
done

[[ "$environment" == staging || "$environment" == release ]] || usage
backup_is_full_sha "$expected_sha" || { echo "ERROR: --expected-sha must be an exact 40-character lowercase Git SHA" >&2; exit 2; }
[[ "$project" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]+$ ]] || { echo "ERROR: invalid Compose project" >&2; exit 2; }
[[ "$max_backup_age" =~ ^[0-9]+$ && "$minimum_free" =~ ^[0-9]+$ ]] || { echo "ERROR: thresholds must be numeric" >&2; exit 2; }
[[ -n "$backup_root" ]] || usage

for command in docker curl df find date; do backup_require "$command"; done
python_bin="${OPS_PYTHON_BIN:-python3}"
if ! command -v "$python_bin" >/dev/null 2>&1; then
  python_bin=python
fi
backup_require "$python_bin"

root="$(backup_shell_path "$backup_root")"
[[ -d "$root" && ! -L "$root" ]] || { echo "ERROR: backup root is missing or unsafe" >&2; exit 1; }

result=0
report() { printf '%-26s %s\n' "$1" "$2"; }
attention() { report "$1" "$2"; result=1; }

status_file="${OPS_CHECK_STATUS_FILE:-}"
if [[ -n "$status_file" && "${OPS_CHECK_TEST_MODE:-}" != 1 ]]; then
  echo "ERROR: OPS_CHECK_STATUS_FILE is test-only" >&2
  exit 2
fi

status_lines="$(OPS_BASE_URL="$base_url" OPS_STATUS_FILE="$status_file" OPS_TEST_MODE="${OPS_CHECK_TEST_MODE:-}" \
  OPS_ADMIN_USERNAME="${OPS_ADMIN_USERNAME:-}" OPS_ADMIN_PASSWORD="${OPS_ADMIN_PASSWORD:-}" "$python_bin" - <<'PY'
import json, os, re, sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

base = os.environ.get("OPS_BASE_URL", "").rstrip("/")
fixture = os.environ.get("OPS_STATUS_FILE", "")
def request(path, method="GET", body=None, headers=None):
    payload = None if body is None else json.dumps(body).encode()
    req = Request(base + path, data=payload, method=method, headers=headers or {})
    with urlopen(req, timeout=5) as response:
        return json.load(response)
try:
    if fixture:
        with open(fixture, encoding="utf-8") as handle: data = json.load(handle)
    else:
        username, password = os.environ.get("OPS_ADMIN_USERNAME"), os.environ.get("OPS_ADMIN_PASSWORD")
        if not username or not password: raise RuntimeError("OPS_ADMIN_USERNAME and OPS_ADMIN_PASSWORD are required")
        login = request("/api/auth/login", "POST", {"username": username, "password": password}, {"Content-Type":"application/json"})
        token = login.get("token")
        if not token: raise RuntimeError("admin login did not return a token")
        headers = {"Authorization": "Bearer " + token}
        version, status = request("/api/admin/version", headers=headers), request("/api/admin/system-status", headers=headers)
        data = dict(status); data["version"] = version
    def clean(value, pattern):
        value = str(value if value is not None else "UNKNOWN")
        return value if re.fullmatch(pattern, value) else "UNKNOWN"
    version, components, queues, outbox = data.get("version", {}), data.get("components", {}), data.get("queues", {}), data.get("outbox", {})
    print("git_sha=" + clean(version.get("gitSha"), r"[0-9a-f]{40}"))
    print("flyway=" + clean(version.get("flywayVersion"), r"\d+"))
    for name in ("backend", "postgresql", "rabbitmq", "worker", "runner"):
        print(name + "=" + clean(components.get(name, {}).get("status"), r"UP|DOWN|UNKNOWN"))
    print("sandbox=" + ("true" if components.get("runner", {}).get("sandboxAvailable") is True else "false"))
    for name in ("main", "retry", "dlq"):
        print("queue_" + name + "=" + clean(queues.get(name), r"-?\d+"))
    print("outbox_nonterminal=" + clean(outbox.get("nonterminal"), r"-?\d+"))
except (RuntimeError, HTTPError, URLError, ValueError, OSError) as error:
    print("ERROR: unable to collect read-only status: " + str(error), file=sys.stderr)
    sys.exit(1)
PY
)" || { echo "ERROR: unable to collect operational status" >&2; exit 1; }

declare -A status=()
while IFS='=' read -r key value; do
  key="${key//$'\r'/}"
  value="${value//$'\r'/}"
  [[ -n "$key" ]] && status["$key"]="$value"
done <<< "$status_lines"

[[ "${status[git_sha]:-UNKNOWN}" == "$expected_sha" ]] && report "deployed SHA" "OK $expected_sha" || attention "deployed SHA" "FAIL expected=$expected_sha actual=${status[git_sha]:-UNKNOWN}"
[[ "${status[flyway]:-UNKNOWN}" == 9 ]] && report "Flyway" "OK V9" || attention "Flyway" "FAIL expected=9 actual=${status[flyway]:-UNKNOWN}"
for component in backend postgresql rabbitmq worker runner; do
  [[ "${status[$component]:-UNKNOWN}" == UP ]] && report "$component" "OK" || attention "$component" "FAIL ${status[$component]:-UNKNOWN}"
done
[[ "${status[sandbox]:-false}" == true ]] && report "sandbox" "OK" || attention "sandbox" "FAIL unavailable"

queue_warn="${OPS_QUEUE_WARN_THRESHOLD:-100}"
outbox_warn="${OPS_OUTBOX_WARN_THRESHOLD:-0}"
[[ "$queue_warn" =~ ^[0-9]+$ && "$outbox_warn" =~ ^[0-9]+$ ]] || { echo "ERROR: queue thresholds must be numeric" >&2; exit 2; }
for queue in main retry dlq; do
  value="${status[queue_$queue]:--1}"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then attention "queue $queue" "UNKNOWN";
  elif [[ "$queue" == dlq && "$value" -gt 0 ]]; then attention "queue $queue" "FAIL depth=$value";
  elif [[ "$value" -gt "$queue_warn" ]]; then attention "queue $queue" "WARN depth=$value";
  else report "queue $queue" "OK depth=$value"; fi
done
outbox_value="${status[outbox_nonterminal]:--1}"
if [[ ! "$outbox_value" =~ ^[0-9]+$ ]]; then attention "Outbox nonterminal" "UNKNOWN";
elif [[ "$outbox_value" -gt "$outbox_warn" ]]; then attention "Outbox nonterminal" "WARN count=$outbox_value";
else report "Outbox nonterminal" "OK count=$outbox_value"; fi

latest=""
for category in daily consistent weekly monthly; do
  [[ -d "$root/$category" ]] || continue
  while IFS= read -r dir; do
    if backup_verify_dir "$dir" >/dev/null 2>&1; then
      created="$(backup_manifest_value "$dir/manifest.json" createdAt)"
      [[ -z "$latest" || "$created" > "${latest%%|*}" ]] && latest="$created|$dir"
    fi
  done < <(find "$root/$category" -mindepth 1 -maxdepth 1 -type d -print)
done
if [[ -z "$latest" ]]; then
  attention "latest verified backup" "FAIL none"
else
  created="${latest%%|*}"; backup_dir="${latest#*|}"
  if [[ "${OPS_CHECK_TEST_MODE:-}" == 1 && -n "${OPS_CHECK_AVAILABLE_BYTES:-}" ]]; then available="${OPS_CHECK_AVAILABLE_BYTES}"; else available="$(backup_free_bytes "$root" || true)"; fi
  now_epoch="$(date -u +%s)"
  created_epoch="$(OPS_CREATED_AT="$created" "$python_bin" - <<'PY'
import calendar, os, sys, time
try: print(calendar.timegm(time.strptime(os.environ["OPS_CREATED_AT"], "%Y-%m-%dT%H%M%SZ")))
except (KeyError, ValueError): sys.exit(1)
PY
)" || created_epoch=""
  if [[ "$created_epoch" =~ ^[0-9]+$ && "$now_epoch" =~ ^[0-9]+$ ]]; then
    age=$((now_epoch-created_epoch))
    [[ "$age" -ge 0 && "$age" -le "$max_backup_age" ]] && report "latest verified backup" "OK ageSeconds=$age" || attention "latest verified backup" "WARN ageSeconds=$age"
  else attention "latest verified backup" "UNKNOWN timestamp"; fi
  [[ "$available" =~ ^[0-9]+$ && "$available" -ge "$minimum_free" ]] && report "disk free space" "OK bytes=$available" || attention "disk free space" "FAIL below minimum"
  report "backup directory" "OK $backup_dir"
fi

if [[ "${OPS_CHECK_TEST_MODE:-}" == 1 ]]; then
  report "container restart state" "TEST-SKIPPED"
else
  containers="$(docker ps -q --filter "label=com.docker.compose.project=$project")"
  if [[ -z "$containers" ]]; then attention "container restart state" "FAIL no project containers";
  else
    restart_problem=false
    while IFS= read -r row; do
      name="${row%%|*}"; restarts="${row##*|}"
      [[ "$restarts" =~ ^[0-9]+$ && "$restarts" -eq 0 ]] || { attention "container $name" "WARN restarts=$restarts"; restart_problem=true; }
    done < <(docker inspect --format '{{.Name}}|{{.RestartCount}}' $containers)
    [[ "$restart_problem" == false ]] && report "container restart state" "OK"
  fi
fi

if [[ "$result" -eq 0 ]]; then
  echo "OPS CHECK: PASS"
else
  echo "OPS CHECK: ATTENTION REQUIRED" >&2
fi
exit "$result"
