#!/usr/bin/env bash

# Runs only inside oj-sandbox-acceptance.service as User=ojrunner.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
expected_unit="oj-sandbox-acceptance.service"
expected_cgroup="/sys/fs/cgroup/system.slice/$expected_unit"

fail() {
  printf 'LINUX ACCEPTANCE FAILED: %s\n' "$*" >&2
  exit 1
}

[[ "${RUNNER_ACCEPTANCE_UNIT:-}" == "$expected_unit" ]] \
  || fail "acceptance unit identity is missing"
[[ "${RUNNER_CGROUP_V2_MOUNT:-}" == "$expected_cgroup" ]] \
  || fail "acceptance cgroup override is missing or unsafe"
[[ "${RUNNER_CGROUP_V2_MOUNT}" != "/sys/fs/cgroup/system.slice/oj-sandbox-runner.service" ]] \
  || fail "production cgroup must never be used by acceptance"
[[ "${RUNNER_SANDBOX_MODE:-}" == "linux" ]] \
  || fail "Linux sandbox mode is required"
[[ -n "${RUNNER_ACCEPTANCE_STAGING_ROOT:-}" ]] \
  || fail "acceptance staging identity is missing"
[[ "$repo_root" == "$RUNNER_ACCEPTANCE_STAGING_ROOT/repo" ]] \
  || fail "acceptance repository is outside its staging root"
[[ "${MAVEN_REPO_LOCAL:-}" == "$RUNNER_ACCEPTANCE_STAGING_ROOT/m2" ]] \
  || fail "dedicated Maven repository must be inside acceptance staging"
[[ "$(id -u)" -eq 10001 ]] || fail "acceptance must run as ojrunner uid 10001"
[[ "$(id -g)" -eq 10001 ]] || fail "acceptance must run as ojrunner gid 10001"

if ! "$repo_root/scripts/runner-linux-preflight.sh"; then
  fail "Runner Linux preflight did not return SUPPORTED"
fi

command -v mvn >/dev/null 2>&1 || fail "Maven is unavailable inside acceptance unit"
mvn -B -f "$repo_root/runner/pom.xml" \
  -Dmaven.repo.local="$MAVEN_REPO_LOCAL" \
  -Plinux-security verify

report="$repo_root/runner/target/failsafe-reports/TEST-com.oj.runner.execution.linux.LinuxSandboxSecurityIT.xml"
[[ -f "$report" ]] || fail "LinuxSecurityIT report was not generated"
suite="$(grep -m1 '<testsuite ' "$report" || true)"
[[ -n "$suite" ]] || fail "LinuxSecurityIT report is malformed"

xml_attribute() {
  local name="$1"
  sed -n "s/.* $name=\"\([0-9][0-9]*\)\".*/\1/p" <<<"$suite"
}

tests="$(xml_attribute tests)"
failures="$(xml_attribute failures)"
errors="$(xml_attribute errors)"
skipped="$(xml_attribute skipped)"
[[ "$tests" =~ ^[1-9][0-9]*$ ]] || fail "LinuxSecurityIT did not execute any tests"
[[ "$failures" == "0" && "$errors" == "0" && "$skipped" == "0" ]] \
  || fail "LinuxSecurityIT was failed, partial, or skipped"

printf 'LinuxSecurityIT executed: tests=%s failures=0 errors=0 skipped=0\n' "$tests"
