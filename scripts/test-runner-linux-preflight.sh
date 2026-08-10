#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
preflight="$repo_root/scripts/runner-linux-preflight.sh"
mock_source="$repo_root/scripts/test-fixtures/runner-preflight-command-mock.sh"
shell_path="$(command -v bash)"
system_path="$PATH"
temp_root="$(mktemp -d)"
trap 'rm -rf -- "$temp_root"' EXIT INT TERM
mock_command="$temp_root/runner-preflight-command-mock"
cp -- "$mock_source" "$mock_command"
chmod 0700 "$mock_command"

fail() {
  printf 'RUNNER PREFLIGHT TEST FAILED: %s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  grep -Fq -- "$expected" "$file" || fail "missing output: $expected"
}

assert_not_exists() {
  [[ ! -e "$1" ]] || fail "unexpected path exists: $1"
}

prepare_mock_bin() {
  local directory="$1"
  shift
  mkdir -p -- "$directory"
  local command_name
  for command_name in "$@"; do
    ln -s -- "$mock_command" "$directory/$command_name"
  done
}

run_preflight() {
  local output="$1"
  shift
  set +e
  env "$@" "$shell_path" "$preflight" >"$output" 2>&1
  local rc=$?
  set -e
  return "$rc"
}

base_bin="$temp_root/base-bin"
prepare_mock_bin "$base_bin" nsjail unshare aa-exec

output="$temp_root/nsjail-ok.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  MOCK_NSJAIL_RC=0 \
  MOCK_UNSHARE_RC=0 || true
assert_contains "$output" "PASS  nsjail executable"

output="$temp_root/nsjail-missing.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$temp_root/missing-nsjail" \
  MOCK_UNSHARE_RC=0 || true
assert_contains "$output" "FAIL  nsjail executable"

output="$temp_root/nsjail-failure.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  MOCK_NSJAIL_RC=9 \
  MOCK_UNSHARE_RC=0 || true
assert_contains "$output" "FAIL  nsjail executable"

direct_log="$temp_root/direct.log"
output="$temp_root/direct.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  PREFLIGHT_TEST_LOG="$direct_log" \
  MOCK_NSJAIL_RC=0 \
  MOCK_UNSHARE_RC=0 || true
assert_contains "$output" "PASS  unprivileged namespace creation"
assert_contains "$direct_log" "unshare"
assert_contains "$direct_log" "--help"
if grep -Fq -- "aa-exec" "$direct_log"; then
  fail "default namespace probe unexpectedly used aa-exec"
fi

apparmor_log="$temp_root/apparmor.log"
sentinel="$temp_root/profile-injection"
profile="oj-runner-preflight; touch $sentinel"
output="$temp_root/apparmor.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  RUNNER_APPARMOR_PREFLIGHT_PROFILE="$profile" \
  PREFLIGHT_TEST_LOG="$apparmor_log" \
  MOCK_NSJAIL_RC=0 \
  MOCK_AA_EXEC_RC=0 || true
assert_contains "$output" "PASS  unprivileged namespace creation"
assert_contains "$apparmor_log" "aa-exec"
assert_contains "$apparmor_log" "-p"
assert_contains "$apparmor_log" "$profile"
assert_contains "$apparmor_log" "--"
assert_contains "$apparmor_log" "unshare"
assert_not_exists "$sentinel"

missing_aa_bin="$temp_root/missing-aa-bin"
prepare_mock_bin "$missing_aa_bin" nsjail unshare
output="$temp_root/aa-exec-missing.out"
run_preflight "$output" \
  PATH="$missing_aa_bin" \
  RUNNER_NSJAIL_PATH="$missing_aa_bin/nsjail" \
  RUNNER_APPARMOR_PREFLIGHT_PROFILE=oj-runner-preflight \
  MOCK_NSJAIL_RC=0 || true
assert_contains "$output" "FAIL  unprivileged namespace creation"

output="$temp_root/aa-exec-failure.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  RUNNER_APPARMOR_PREFLIGHT_PROFILE=oj-runner-preflight \
  MOCK_NSJAIL_RC=0 \
  MOCK_AA_EXEC_RC=9 || true
assert_contains "$output" "FAIL  unprivileged namespace creation"

output="$temp_root/unshare-failure.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  MOCK_NSJAIL_RC=0 \
  MOCK_UNSHARE_RC=9 || true
assert_contains "$output" "FAIL  unprivileged namespace creation"

output="$temp_root/host-boundaries.out"
run_preflight "$output" \
  PATH="$base_bin:$system_path" \
  RUNNER_NSJAIL_PATH="$base_bin/nsjail" \
  MOCK_NSJAIL_RC=0 \
  MOCK_UNSHARE_RC=0 || true
if [[ -e /.dockerenv ]]; then
  assert_contains "$output" "FAIL  ordinary Docker container is not an accepted Runner host"
else
  kernel_text="$(uname -r 2>/dev/null || true)"
  if [[ "${kernel_text,,}" == *microsoft* || "${kernel_text,,}" == *wsl* ]]; then
    assert_contains "$output" "FAIL  WSL/Docker Desktop is not an accepted security host"
  fi
fi
grep -Eq 'cgroup v2 mounted|cgroup v2 is not mounted' "$output" \
  || fail "cgroup v2 check disappeared"
grep -Fq 'delegated cgroup root' "$output" || fail "delegated cgroup check disappeared"
grep -Fq 'runtime rootfs' "$output" || fail "runtime rootfs check disappeared"
grep -Fq 'workspace root' "$output" || fail "workspace check disappeared"
grep -Fq 'seccomp policy' "$output" || fail "seccomp policy check disappeared"

printf 'Runner Linux preflight compatibility checks passed.\n'
