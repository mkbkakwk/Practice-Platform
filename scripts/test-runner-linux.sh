#!/usr/bin/env bash

# Host-side orchestrator for the dedicated Linux security acceptance suite.
# Root is used only to create the transient systemd unit and its staging tree;
# preflight, Maven, and LinuxSecurityIT always run as the unprivileged ojrunner.
set -euo pipefail

readonly ACCEPTANCE_UNIT="oj-sandbox-acceptance.service"
readonly PRODUCTION_UNIT="oj-sandbox-runner.service"
readonly ACCEPTANCE_PARENT="/run/oj-sandbox-acceptance"
readonly ACCEPTANCE_CGROUP="/sys/fs/cgroup/system.slice/$ACCEPTANCE_UNIT"
readonly PRODUCTION_CGROUP="/sys/fs/cgroup/system.slice/$PRODUCTION_UNIT"
readonly ACCEPTANCE_WORKSPACE="/run/oj-sandbox-runner/jobs"
readonly RUNTIME_ROOTFS="/srv/oj-sandbox-runner/rootfs"
readonly SECCOMP_POLICY="/etc/oj-sandbox-runner/nsjail-seccomp.policy"
readonly NSJAIL_PATH="/opt/oj-sandbox-runner/bin/nsjail"

repo_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
staging_root=""
cleanup_complete=0
result_state="NOT RUN"
declare -a SYSTEMD_RUN_ARGS=()

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

effective_protection_value() {
  local property="$1"
  local value
  value="$(systemctl show "$PRODUCTION_UNIT" --property="$property" --value 2>/dev/null)" \
    || fail "cannot read $property from $PRODUCTION_UNIT"
  case "$value" in
    yes|no) printf '%s\n' "$value" ;;
    *) fail "$PRODUCTION_UNIT returned an invalid $property value" ;;
  esac
}

build_systemd_run_args() {
  local stage="$1"
  local staged_repo="$2"
  local maven_repo="$3"
  local home="$4"
  local protect_kernel_tunables="$5"
  local protect_kernel_logs="$6"

  SYSTEMD_RUN_ARGS=(
    "--unit=$ACCEPTANCE_UNIT"
    --service-type=exec
    --wait
    --collect
    --pipe
    "--working-directory=$staged_repo"
    "--property=User=ojrunner"
    "--property=Group=ojrunner"
    "--property=UMask=0077"
    "--property=Delegate=cpu memory pids"
    "--property=DelegateSubgroup=runner"
    "--property=NoNewPrivileges=yes"
    "--property=CapabilityBoundingSet="
    "--property=AmbientCapabilities="
    "--property=PrivateDevices=yes"
    "--property=PrivateTmp=yes"
    "--property=ProtectSystem=strict"
    "--property=ProtectHome=yes"
    "--property=ProtectControlGroups=no"
    "--property=ProtectKernelTunables=$protect_kernel_tunables"
    "--property=ProtectKernelModules=yes"
    "--property=ProtectKernelLogs=$protect_kernel_logs"
    "--property=LockPersonality=yes"
    "--property=RestrictRealtime=yes"
    "--property=RestrictSUIDSGID=yes"
    "--property=RemoveIPC=yes"
    "--property=KillMode=control-group"
    "--property=TimeoutStopSec=30s"
    "--property=ReadOnlyPaths=$RUNTIME_ROOTFS $SECCOMP_POLICY"
    "--property=ReadWritePaths=$stage"
    "--property=TemporaryFileSystem=$ACCEPTANCE_WORKSPACE:rw,nosuid,nodev,exec,size=256M,mode=0700,uid=10001,gid=10001"
    "--setenv=HOME=$home"
    "--setenv=MAVEN_REPO_LOCAL=$maven_repo"
    "--setenv=RUNNER_ACCEPTANCE_STAGING_ROOT=$stage"
    "--setenv=PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    "--setenv=LANG=C.UTF-8"
    "--setenv=RUNNER_SANDBOX_MODE=linux"
    "--setenv=RUNNER_NSJAIL_PATH=$NSJAIL_PATH"
    "--setenv=RUNNER_SANDBOX_ROOTFS=$RUNTIME_ROOTFS"
    "--setenv=RUNNER_WORKSPACE_ROOT=$ACCEPTANCE_WORKSPACE"
    "--setenv=RUNNER_SECCOMP_POLICY=$SECCOMP_POLICY"
    "--setenv=RUNNER_CGROUP_V2_MOUNT=$ACCEPTANCE_CGROUP"
    "--setenv=RUNNER_ACCEPTANCE_UNIT=$ACCEPTANCE_UNIT"
  )
  if [[ -n "${RUNNER_APPARMOR_PREFLIGHT_PROFILE:-}" ]]; then
    SYSTEMD_RUN_ARGS+=(
      "--setenv=RUNNER_APPARMOR_PREFLIGHT_PROFILE=$RUNNER_APPARMOR_PREFLIGHT_PROFILE")
  fi
}

print_unit_plan() {
  build_systemd_run_args \
    "$ACCEPTANCE_PARENT/plan" \
    "$ACCEPTANCE_PARENT/plan/repo" \
    "$ACCEPTANCE_PARENT/plan/m2" \
    "$ACCEPTANCE_PARENT/plan/home" \
    "${RUNNER_ACCEPTANCE_PLAN_PROTECT_KERNEL_TUNABLES:-no}" \
    "${RUNNER_ACCEPTANCE_PLAN_PROTECT_KERNEL_LOGS:-no}"
  printf '%s\n' "${SYSTEMD_RUN_ARGS[@]}"
  printf '%s\n' "/usr/bin/bash" \
    "$ACCEPTANCE_PARENT/plan/repo/scripts/runner-linux-acceptance-inner.sh"
}

acceptance_cleanup() {
  local cleanup_rc=0
  local attempt

  if command -v systemctl >/dev/null 2>&1 \
    && systemctl show "$ACCEPTANCE_UNIT" >/dev/null 2>&1; then
    if ! systemctl stop "$ACCEPTANCE_UNIT" >/dev/null 2>&1 \
      && systemctl show "$ACCEPTANCE_UNIT" >/dev/null 2>&1; then
      cleanup_rc=1
    fi
    if systemctl show "$ACCEPTANCE_UNIT" >/dev/null 2>&1 \
      && ! systemctl reset-failed "$ACCEPTANCE_UNIT" >/dev/null 2>&1 \
      && systemctl show "$ACCEPTANCE_UNIT" >/dev/null 2>&1; then
      cleanup_rc=1
    fi
  fi

  for attempt in {1..50}; do
    if [[ ! -e "$ACCEPTANCE_CGROUP" ]] \
      && ! systemctl show "$ACCEPTANCE_UNIT" >/dev/null 2>&1; then
      break
    fi
    sleep 0.1
  done
  if systemctl show "$ACCEPTANCE_UNIT" >/dev/null 2>&1; then
    printf 'ERROR: transient acceptance unit was not removed: %s\n' \
      "$ACCEPTANCE_UNIT" >&2
    cleanup_rc=1
  fi
  if [[ -e "$ACCEPTANCE_CGROUP" ]]; then
    printf 'ERROR: acceptance cgroup was not removed: %s\n' "$ACCEPTANCE_CGROUP" >&2
    cleanup_rc=1
  fi

  if [[ -n "$staging_root" ]]; then
    case "$staging_root" in
      "$ACCEPTANCE_PARENT"/run.*)
        rm -rf --one-file-system -- "$staging_root" || cleanup_rc=1
        ;;
      *)
        printf 'ERROR: refusing to remove unsafe acceptance staging path: %s\n' \
          "$staging_root" >&2
        cleanup_rc=1
        ;;
    esac
  fi

  cleanup_complete=1
  return "$cleanup_rc"
}

acceptance_exit() {
  local original_rc=$?
  local cleanup_rc=0
  trap - EXIT INT TERM
  set +e
  if [[ $cleanup_complete -eq 0 ]]; then
    acceptance_cleanup
    cleanup_rc=$?
  fi
  if [[ $original_rc -eq 0 && $cleanup_rc -ne 0 ]]; then
    original_rc=$cleanup_rc
  fi
  if [[ "$result_state" != "PASSED" ]]; then
    printf 'Linux isolation tests: %s\n' "$result_state" >&2
  fi
  exit "$original_rc"
}

if [[ "${1:-}" == "--print-unit-plan" ]]; then
  print_unit_plan
  exit 0
fi

trap acceptance_exit EXIT
trap 'exit 130' INT TERM

[[ "$(uname -s 2>/dev/null || true)" == "Linux" ]] \
  || fail "dedicated Linux host required"
[[ "${EUID:-$(id -u)}" -eq 0 ]] \
  || fail "run this orchestrator through sudo; Maven still runs as ojrunner"

for command in git tar systemctl systemd-run getent install mktemp; do
  command -v "$command" >/dev/null 2>&1 || fail "required host command missing: $command"
done
getent passwd ojrunner >/dev/null || fail "ojrunner user is unavailable"
getent group ojrunner >/dev/null || fail "ojrunner group is unavailable"
command -v mvn >/dev/null 2>&1 || fail "Maven is required on the acceptance host"
[[ -x "$NSJAIL_PATH" ]] || fail "pinned nsjail is unavailable: $NSJAIL_PATH"

load_state="$(systemctl show "$PRODUCTION_UNIT" --property=LoadState --value 2>/dev/null || true)"
[[ "$load_state" == "loaded" ]] || fail "$PRODUCTION_UNIT must be loaded to mirror effective hardening"
protect_kernel_tunables="$(effective_protection_value ProtectKernelTunables)"
protect_kernel_logs="$(effective_protection_value ProtectKernelLogs)"

acceptance_state="$(systemctl show "$ACCEPTANCE_UNIT" \
  --property=ActiveState --value 2>/dev/null || true)"
case "$acceptance_state" in
  active|activating|reloading|deactivating)
    fail "$ACCEPTANCE_UNIT is already running"
    ;;
esac
[[ ! -e "$ACCEPTANCE_CGROUP" ]] \
  || fail "stale acceptance cgroup exists: $ACCEPTANCE_CGROUP"
[[ "$ACCEPTANCE_CGROUP" != "$PRODUCTION_CGROUP" ]] \
  || fail "acceptance must not use the production cgroup"

git_status="$(git -c "safe.directory=$repo_root" -C "$repo_root" status --porcelain)"
[[ -z "$git_status" ]] || fail "acceptance requires a clean committed working tree"
for forbidden in .env .env.production .env.staging; do
  if git -c "safe.directory=$repo_root" -C "$repo_root" cat-file -e "HEAD:$forbidden" 2>/dev/null; then
    fail "refusing to stage tracked runtime environment file: $forbidden"
  fi
done

install -d -o root -g root -m 0755 "$ACCEPTANCE_PARENT"
staging_root="$(mktemp -d "$ACCEPTANCE_PARENT/run.XXXXXX")"
staged_repo="$staging_root/repo"
maven_repo="$staging_root/m2"
acceptance_home="$staging_root/home"
mkdir -p -- "$staged_repo" "$maven_repo" "$acceptance_home"

source_archive="$staging_root/source.tar"
git -c "safe.directory=$repo_root" -C "$repo_root" archive \
  --format=tar --output="$source_archive" HEAD -- \
  runner scripts/runner-linux-preflight.sh scripts/runner-linux-acceptance-inner.sh
tar -xf "$source_archive" -C "$staged_repo"
rm -f -- "$source_archive"
chown -R ojrunner:ojrunner "$staging_root"
chmod 0700 "$staging_root" "$maven_repo" "$acceptance_home"

build_systemd_run_args \
  "$staging_root" "$staged_repo" "$maven_repo" "$acceptance_home" \
  "$protect_kernel_tunables" "$protect_kernel_logs"

result_state="FAILED"
systemd-run "${SYSTEMD_RUN_ARGS[@]}" \
  /usr/bin/bash "$staged_repo/scripts/runner-linux-acceptance-inner.sh"

if ! acceptance_cleanup; then
  fail "acceptance cleanup failed"
fi
result_state="PASSED"
trap - EXIT INT TERM
printf 'Linux isolation tests: PASSED\n'
