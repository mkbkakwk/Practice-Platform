#!/usr/bin/env bash

# Static regression coverage for the dedicated-host acceptance orchestrator.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
harness="$repo_root/scripts/test-runner-linux.sh"
inner="$repo_root/scripts/runner-linux-acceptance-inner.sh"
pom="$repo_root/runner/pom.xml"

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

grep -Fq 'runner-linux-preflight.sh' "$inner" \
  || fail "acceptance unit does not execute preflight"
grep -Fq -- '-Plinux-security verify' "$inner" \
  || fail "acceptance unit does not execute the Linux security Maven profile"
grep -Fq 'LinuxSandboxSecurityIT.xml' "$inner" \
  || fail "acceptance unit does not verify the Failsafe report"
grep -Fq '"$skipped" == "0"' "$inner" \
  || fail "acceptance unit can accept skipped Linux security tests"
grep -Fq 'git -c "safe.directory=$repo_root" -C "$repo_root" archive' "$harness" \
  || fail "acceptance sources are not staged from committed Git content"
grep -Fq 'runner scripts/runner-linux-preflight.sh scripts/runner-linux-acceptance-inner.sh' "$harness" \
  || fail "acceptance staging scope is not minimal and explicit"
grep -Fq '<failIfNoTests>true</failIfNoTests>' "$pom" \
  || fail "Failsafe does not fail when LinuxSecurityIT is absent"

printf 'Runner Linux acceptance harness static checks passed.\n'
