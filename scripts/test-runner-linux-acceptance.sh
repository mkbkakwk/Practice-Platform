#!/usr/bin/env bash

# Static regression coverage for the dedicated-host acceptance orchestrator.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
harness="$repo_root/scripts/test-runner-linux.sh"
inner="$repo_root/scripts/runner-linux-acceptance-inner.sh"
library="$repo_root/scripts/runner-linux-acceptance-lib.sh"
pom="$repo_root/runner/pom.xml"
linux_security_it="$repo_root/runner/src/test/java/com/oj/runner/execution/linux/LinuxSandboxSecurityIT.java"
temp_root="$(mktemp -d)"
trap 'rm -rf -- "$temp_root"' EXIT INT TERM

source "$library"

fail() {
  printf 'RUNNER ACCEPTANCE HARNESS TEST FAILED: %s\n' "$*" >&2
  exit 1
}

assert_plan_line() {
  local expected="$1"
  grep -Fxq -- "$expected" <<<"$plan" || fail "unit plan is missing: $expected"
}

plan="$(
  RUNNER_APPARMOR_PREFLIGHT_PROFILE=oj-runner-preflight \
  RUNNER_ACCEPTANCE_PLAN_PROTECT_KERNEL_TUNABLES=no \
  RUNNER_ACCEPTANCE_PLAN_PROTECT_KERNEL_LOGS=no \
    bash "$harness" --print-unit-plan
)"

for expected in \
  '--unit=oj-sandbox-acceptance.service' \
  '--property=User=ojrunner' \
  '--property=Group=ojrunner' \
  '--property=Delegate=cpu memory pids' \
  '--property=DelegateSubgroup=runner' \
  '--property=NoNewPrivileges=yes' \
  '--property=CapabilityBoundingSet=' \
  '--property=AmbientCapabilities=' \
  '--property=PrivateDevices=yes' \
  '--property=PrivateTmp=yes' \
  '--property=ProtectSystem=strict' \
  '--property=ProtectHome=yes' \
  '--property=ProtectControlGroups=no' \
  '--property=ProtectKernelTunables=no' \
  '--property=ProtectKernelLogs=no' \
  '--property=LockPersonality=yes' \
  '--property=RestrictRealtime=yes' \
  '--property=RestrictSUIDSGID=yes' \
  '--property=RemoveIPC=yes' \
  '--property=KillMode=control-group' \
  '--property=ReadOnlyPaths=/srv/oj-sandbox-runner/rootfs /etc/oj-sandbox-runner/nsjail-seccomp.policy' \
  '--property=ReadWritePaths=/run/oj-sandbox-acceptance/plan' \
  '--property=TemporaryFileSystem=/run/oj-sandbox-runner/jobs:rw,nosuid,nodev,exec,size=256M,mode=0700,uid=10001,gid=10001' \
  '--setenv=RUNNER_NSJAIL_PATH=/opt/oj-sandbox-runner/bin/nsjail' \
  '--setenv=RUNNER_SANDBOX_ROOTFS=/srv/oj-sandbox-runner/rootfs' \
  '--setenv=RUNNER_WORKSPACE_ROOT=/run/oj-sandbox-runner/jobs' \
  '--setenv=RUNNER_SECCOMP_POLICY=/etc/oj-sandbox-runner/nsjail-seccomp.policy' \
  '--setenv=RUNNER_CGROUP_V2_MOUNT=/sys/fs/cgroup/system.slice/oj-sandbox-acceptance.service' \
  '--setenv=RUNNER_ACCEPTANCE_STAGING_ROOT=/run/oj-sandbox-acceptance/plan' \
  '--setenv=RUNNER_APPARMOR_PREFLIGHT_PROFILE=oj-runner-preflight'; do
  assert_plan_line "$expected"
done

if grep -Fq -- '/sys/fs/cgroup/system.slice/oj-sandbox-runner.service' <<<"$plan"; then
  fail "unit plan uses the production cgroup path"
fi
if grep -Fq -- '--property=ProtectHome=no' <<<"$plan"; then
  fail "unit plan disables ProtectHome"
fi
if grep -Fq -- '--setenv=RUNNER_SANDBOX_MODE=linux' <<<"$plan"; then
  fail "unit plan pollutes ordinary Surefire tests with Linux sandbox mode"
fi

grep -Fq 'trap acceptance_exit EXIT' "$harness" \
  || fail "host orchestrator has no cleanup exit trap"
grep -Fq 'systemctl stop "$ACCEPTANCE_UNIT"' "$harness" \
  || fail "host orchestrator does not stop its transient unit"
grep -Fq 'transient acceptance unit was not removed' "$harness" \
  || fail "host orchestrator does not verify transient-unit cleanup"
grep -Fq 'rm -rf --one-file-system -- "$staging_root"' "$harness" \
  || fail "host orchestrator does not clean its bounded staging directory"
grep -Fq '[[ ! -e "$ACCEPTANCE_CGROUP" ]]' "$harness" \
  || fail "host orchestrator does not verify cgroup cleanup"

run_line="$(grep -n '^systemd-run ' "$harness" | cut -d: -f1)"
cleanup_line="$(grep -n '^if ! acceptance_cleanup; then' "$harness" | cut -d: -f1)"
pass_line="$(grep -n "^printf 'Linux isolation tests: PASSED" "$harness" | cut -d: -f1)"
[[ -n "$run_line" && -n "$cleanup_line" && -n "$pass_line" ]] \
  || fail "acceptance execution/cleanup/success sequence is incomplete"
[[ "$run_line" -lt "$cleanup_line" && "$cleanup_line" -lt "$pass_line" ]] \
  || fail "PASSED can be printed before execution and cleanup finish"
[[ "$(grep -c 'Linux isolation tests: PASSED' "$harness")" -eq 1 ]] \
  || fail "PASSED must have one guarded output site"

grep -Fq 'if ! /usr/bin/bash "$repo_root/scripts/runner-linux-preflight.sh"; then' "$inner" \
  || fail "acceptance unit does not read staged preflight through trusted system Bash"
if grep -Eq '^[[:space:]]*(if ! )?"\$repo_root/scripts/[^\"]+\.sh"' "$inner"; then
  fail "acceptance unit directly executes a staged shell script from noexec /run"
fi
grep -Fq -- '-Plinux-security verify' "$inner" \
  || fail "acceptance unit does not execute the Linux security Maven profile"
if grep -Fq 'RUNNER_SANDBOX_MODE' "$inner"; then
  fail "acceptance inner script requires a Maven-process-wide sandbox mode"
fi
if grep -Eq -- '(-DskipTests|skipTests|maven\.test\.skip)' "$inner"; then
  fail "acceptance inner script skips ordinary or Linux security tests"
fi
grep -Fq 'LinuxSandboxSecurityIT.xml' "$inner" \
  || fail "acceptance unit does not verify the Failsafe report"
grep -Fq '"$skipped" == "0"' "$inner" \
  || fail "acceptance unit can accept skipped Linux security tests"
grep -Fq 'git -c "safe.directory=$repo_root" -C "$repo_root" archive' "$harness" \
  || fail "acceptance sources are not staged from committed Git content"
grep -Fq 'runner test/fixtures/runner \' "$harness" \
  || fail "acceptance staging omits the committed Runner contract fixtures"
archive_block="$(sed -n \
  '/git -c "safe.directory=\$repo_root" -C "\$repo_root" archive/,/tar -xf/p' "$harness")"
for forbidden in .git .env .env.production .env.staging; do
  if grep -Eq "(^|[[:space:]/])${forbidden//./\\.}([[:space:]\\]|$)" <<<"$archive_block"; then
    fail "acceptance archive includes forbidden path: $forbidden"
  fi
done
grep -Fq 'find "$staged_repo" -name "$forbidden" -print -quit' "$harness" \
  || fail "acceptance staging does not scan extracted content for forbidden runtime paths"
grep -Fq '<failIfNoTests>true</failIfNoTests>' "$pom" \
  || fail "Failsafe does not fail when LinuxSecurityIT is absent"
grep -Fq '<runner.sandbox.mode>linux</runner.sandbox.mode>' "$pom" \
  || fail "Failsafe Linux security profile does not force Linux sandbox mode"
grep -Fq '"runner.sandbox.mode=linux"' "$linux_security_it" \
  || fail "LinuxSandboxSecurityIT does not explicitly force Linux sandbox mode"

MOCK_LOAD_STATE=not-found
systemctl() {
  case "${1:-}" in
    show) printf '%s\n' "$MOCK_LOAD_STATE" ;;
    *) return 0 ;;
  esac
}

set +e
acceptance_unit_exists oj-sandbox-acceptance.service
unit_rc=$?
set -e
[[ $unit_rc -eq 1 ]] || fail "LoadState=not-found was treated as an existing unit"

for existing_state in loaded masked error; do
  MOCK_LOAD_STATE="$existing_state"
  acceptance_unit_exists oj-sandbox-acceptance.service \
    || fail "LoadState=$existing_state was treated as a missing unit"
done

MOCK_LOAD_STATE=not-found
missing_cgroup="$temp_root/missing-cgroup"
acceptance_resources_removed oj-sandbox-acceptance.service "$missing_cgroup" \
  || fail "not-found unit with no cgroup did not satisfy cleanup"
mkdir -p -- "$temp_root/existing-cgroup"
if acceptance_resources_removed oj-sandbox-acceptance.service "$temp_root/existing-cgroup"; then
  fail "existing acceptance cgroup was accepted as cleaned"
fi

git() {
  printf '%s' "${MOCK_GIT_STATUS:-}"
}

make_source_tree() {
  local root="$1"
  mkdir -p -- "$root/runner"
  : > "$root/runner/tracked.txt"
}

safe_tree="$temp_root/safe-tree"
make_source_tree "$safe_tree"
: > "$safe_tree/runner/.attach_pid123"
MOCK_GIT_STATUS=
prepare_acceptance_source_tree "$safe_tree" \
  || fail "safe numeric JVM attach marker was rejected"
[[ ! -e "$safe_tree/runner/.attach_pid123" ]] \
  || fail "safe numeric JVM attach marker was not removed"

malformed_tree="$temp_root/malformed-tree"
make_source_tree "$malformed_tree"
: > "$malformed_tree/runner/.attach_pidabc"
MOCK_GIT_STATUS='?? runner/.attach_pidabc'
if prepare_acceptance_source_tree "$malformed_tree"; then
  fail "malformed JVM attach marker was accepted"
fi
[[ -f "$malformed_tree/runner/.attach_pidabc" ]] \
  || fail "malformed JVM attach marker was unexpectedly removed"

symlink_tree="$temp_root/symlink-tree"
make_source_tree "$symlink_tree"
ln -s -- tracked.txt "$symlink_tree/runner/.attach_pid123"
if [[ -L "$symlink_tree/runner/.attach_pid123" ]]; then
  MOCK_GIT_STATUS='?? runner/.attach_pid123'
  if prepare_acceptance_source_tree "$symlink_tree"; then
    fail "symlink JVM attach marker was accepted"
  fi
  [[ -L "$symlink_tree/runner/.attach_pid123" ]] \
    || fail "symlink JVM attach marker was unexpectedly removed"
else
  rm -f -- "$symlink_tree/runner/.attach_pid123"
  printf 'Symlink marker regression requires a POSIX symlink-capable test filesystem.\n'
fi

dirty_tree="$temp_root/dirty-tree"
make_source_tree "$dirty_tree"
: > "$dirty_tree/runner/unrelated.tmp"
MOCK_GIT_STATUS='?? runner/unrelated.tmp'
if prepare_acceptance_source_tree "$dirty_tree"; then
  fail "unrelated untracked file was accepted"
fi
[[ -f "$dirty_tree/runner/unrelated.tmp" ]] \
  || fail "unrelated untracked file was unexpectedly removed"

non_executable_script="$temp_root/non-executable-staged-script.sh"
printf '%s\n' '#!/usr/bin/env bash' 'printf staged-script-read-ok' > "$non_executable_script"
chmod 0600 "$non_executable_script"
[[ "$(bash "$non_executable_script")" == "staged-script-read-ok" ]] \
  || fail "trusted Bash cannot read a non-executable staged script"

printf 'Runner Linux acceptance harness static checks passed.\n'
