#!/usr/bin/env bash

# Real Linux-only acceptance for Dockerized nsjail. This script must never run
# on Docker Desktop/WSL and prints PASSED only after test and cleanup success.
set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
project_name="${RUNNER_DOCKER_PROJECT_NAME:-practice-platform-runner-acceptance}"
compose_file="$repo_root/docker-compose.runner-acceptance.yml"
acceptance_unit="${RUNNER_DOCKER_ACCEPTANCE_UNIT:-oj-docker-runner-acceptance.service}"
acceptance_port="${RUNNER_DOCKER_PORT:-19090}"
test_log="$(mktemp)"
unit_started=false
compose_started=false
cgroup_root=""
compose=(docker compose -p "$project_name" -f "$compose_file")

fail() {
  printf 'LINUX DOCKER ACCEPTANCE FAILED: %s\n' "$*" >&2
  exit 1
}

unit_load_state() {
  systemctl --user show --property=LoadState --value "$acceptance_unit" 2>/dev/null || true
}

cleanup() {
  local cleanup_rc=0
  if [[ "$compose_started" == true ]]; then
    "${compose[@]}" down --remove-orphans || cleanup_rc=1
    compose_started=false
  fi
  if [[ "$unit_started" == true ]] && [[ "$(unit_load_state)" != "not-found" ]]; then
    systemctl --user stop "$acceptance_unit" >/dev/null 2>&1 || cleanup_rc=1
  fi
  for _ in {1..50}; do
    [[ "$(unit_load_state)" == "not-found" ]] && break
    sleep 0.1
  done
  if [[ "$(unit_load_state)" != "not-found" ]]; then
    printf 'ERROR: transient Docker acceptance unit was not collected\n' >&2
    cleanup_rc=1
  fi
  if [[ -n "$cgroup_root" && -e "$cgroup_root" ]]; then
    printf 'ERROR: Docker acceptance cgroup remains: %s\n' "$cgroup_root" >&2
    cleanup_rc=1
  fi
  rm -f -- "$test_log"
  return "$cleanup_rc"
}

cleanup_on_exit() {
  local rc=$?
  trap - EXIT
  cleanup || rc=1
  exit "$rc"
}
trap cleanup_on_exit EXIT INT TERM

[[ "$(uname -s)" == "Linux" ]] || fail "a real Linux host is required"
if grep -Eqi 'microsoft|wsl' /proc/version; then
  fail "WSL and Docker Desktop are not security acceptance hosts"
fi
command -v docker >/dev/null 2>&1 || fail "Docker Engine is required"
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required"
command -v systemd-run >/dev/null 2>&1 || fail "systemd-run is required for scoped cgroup delegation"
systemctl --user is-system-running >/dev/null 2>&1 \
  || [[ "$(systemctl --user is-system-running 2>/dev/null || true)" == "degraded" ]] \
  || fail "the user systemd manager is unavailable"

[[ "$(unit_load_state)" == "not-found" ]] \
  || fail "acceptance unit already exists: $acceptance_unit"

uid="$(id -u)"
gid="$(id -g)"
systemd-run --user \
  --unit="$acceptance_unit" \
  --collect \
  --property="Delegate=cpu memory pids" \
  --property="DelegateSubgroup=manager" \
  /usr/bin/sleep infinity >/dev/null
unit_started=true

control_group="$(systemctl --user show --property=ControlGroup --value "$acceptance_unit")"
[[ "$control_group" == /user.slice/user-"$uid".slice/user@"$uid".service/* ]] \
  || fail "acceptance delegation escaped the current user manager"
cgroup_root="/sys/fs/cgroup$control_group"
for _ in {1..50}; do
  [[ -d "$cgroup_root" ]] && break
  sleep 0.1
done
[[ -d "$cgroup_root" && ! -L "$cgroup_root" ]] || fail "delegated cgroup root is unavailable"
[[ "$(stat -c %u "$cgroup_root")" == "$uid" ]] || fail "delegated cgroup owner is not the acceptance user"
[[ "$(stat -c %g "$cgroup_root")" == "$gid" ]] || fail "delegated cgroup group is not the acceptance group"
[[ ! -s "$cgroup_root/cgroup.procs" ]] || fail "delegated cgroup root contains processes"
for controller in cpu memory pids; do
  grep -qw -- "$controller" "$cgroup_root/cgroup.controllers" \
    || fail "delegated controller is unavailable: $controller"
done

export RUNNER_DOCKER_UID="$uid"
export RUNNER_DOCKER_GID="$gid"
export RUNNER_DOCKER_CGROUP_ROOT="$cgroup_root"
export RUNNER_DOCKER_PORT="$acceptance_port"

printf '==> Building Dockerized Runner and Linux security test images\n'
"${compose[@]}" build runner runner-security-test

printf '==> Starting Dockerized Runner\n'
compose_started=true
"${compose[@]}" up -d --wait runner

health="$(curl -fsS "http://127.0.0.1:$acceptance_port/api/health")"
grep -Eq '"ok"[[:space:]]*:[[:space:]]*true' <<<"$health" || fail "Runner health did not report ok=true"
grep -Eq '"sandboxAvailable"[[:space:]]*:[[:space:]]*true' <<<"$health" \
  || fail "Runner health did not report sandboxAvailable=true"

runner_id="$("${compose[@]}" ps -q runner)"
[[ -n "$runner_id" ]] || fail "Runner container is missing"
[[ "$(docker inspect --format '{{.HostConfig.Privileged}}' "$runner_id")" == "false" ]] \
  || fail "Runner container is privileged"
[[ -z "$(docker inspect --format '{{.HostConfig.PidMode}}' "$runner_id")" ]] \
  || fail "Runner container uses a host/shared PID mode"
[[ "$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$runner_id")" != "host" ]] \
  || fail "Runner container uses host networking"
[[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$runner_id")" == "true" ]] \
  || fail "Runner container root filesystem is writable"
security_options="$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "$runner_id")"
grep -Fq 'no-new-privileges' <<<"$security_options" || fail "no-new-privileges is missing"
grep -Fq 'seccomp=' <<<"$security_options" || fail "a project-owned Docker seccomp profile is missing"
grep -Fq 'apparmor=' <<<"$security_options" || fail "the AppArmor profile is missing"
grep -Fq 'unconfined' <<<"$security_options" && fail "an unconfined security option is forbidden"
mounts="$(docker inspect --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}' "$runner_id")"
grep -Fq '/var/run/docker.sock' <<<"$mounts" && fail "Docker socket is mounted"
grep -Fq -- "$cgroup_root -> /run/oj-sandbox-runner/cgroup" <<<"$mounts" \
  || fail "the exact delegated cgroup subtree is not mounted"

printf '==> Running real LinuxSandboxSecurityIT inside Docker\n'
set +e
"${compose[@]}" run --rm --no-deps runner-security-test 2>&1 | tee "$test_log"
test_rc=${PIPESTATUS[0]}
set -e
[[ $test_rc -eq 0 ]] || fail "LinuxSandboxSecurityIT failed"
grep -Eq 'Tests run: 15, Failures: 0, Errors: 0, Skipped: 0' "$test_log" \
  || fail "LinuxSandboxSecurityIT did not report an exact 15/15 result"
grep -Fq 'BUILD SUCCESS' "$test_log" || fail "Maven did not report BUILD SUCCESS"

if find "$cgroup_root" -mindepth 1 -maxdepth 2 \
    \( -name 'RUNNER.*' -o -name 'NSJAIL.*' \) -print -quit | grep -q .; then
  fail "sandbox execution cgroup residue remains"
fi

printf '==> Cleaning Docker acceptance resources\n'
cleanup
trap - EXIT INT TERM
printf 'Linux isolation tests: PASSED\n'
