#!/usr/bin/env bash

# Static regression coverage for the Dockerized Linux acceptance boundary.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
compose="$repo_root/docker-compose.runner-acceptance.yml"
dockerfile="$repo_root/runner/Dockerfile"
harness="$repo_root/scripts/test-runner-docker-linux.sh"
seccomp="$repo_root/runner/security/docker-runner-seccomp.json"

fail() {
  printf 'RUNNER DOCKER ACCEPTANCE TEST FAILED: %s\n' "$*" >&2
  exit 1
}

for file in "$compose" "$dockerfile" "$harness" "$seccomp"; do
  [[ -f "$file" ]] || fail "missing $file"
done

bash -n "$harness" || fail "Docker Linux acceptance harness has invalid Bash syntax"
grep -Fq 'ARG NSJAIL_REVISION=898e7e042e1bb3a10e54817d71c9eabdcc8e7089' "$dockerfile" \
  || fail "nsjail PR #287 revision is not pinned"
grep -Fq 'target: /run/oj-sandbox-runner/cgroup' "$compose" \
  || fail "the dedicated cgroup subtree mount is missing"
grep -Fq 'RUNNER_SANDBOX_CONTAINERIZED: "true"' "$compose" \
  || fail "explicit container mode is missing"
grep -Fq 'read_only: true' "$compose" || fail "Runner root filesystem is not read-only"
grep -Fq -- '- ALL' "$compose" || fail "Runner capabilities are not dropped"
grep -Fq 'no-new-privileges:true' "$compose" || fail "no-new-privileges is missing"
grep -Fq 'seccomp=./runner/security/docker-runner-seccomp.json' "$compose" \
  || fail "the project-owned Docker seccomp profile is missing"

for forbidden in \
  'privileged: true' \
  'seccomp=unconfined' \
  '/var/run/docker.sock' \
  'network_mode: host' \
  'pid: host' \
  'cap_add:'; do
  if grep -Fq -- "$forbidden" "$compose"; then
    fail "forbidden Docker privilege is present: $forbidden"
  fi
done

grep -Fq 'systemd-run --user' "$harness" || fail "user-scoped systemd delegation is missing"
grep -Fq 'Delegate=cpu memory pids' "$harness" || fail "bounded controller delegation is missing"
grep -Fq 'DelegateSubgroup=manager' "$harness" || fail "delegated root process separation is missing"
grep -Fq 'Linux isolation tests: PASSED' "$harness" || fail "success marker is missing"
grep -Fq 'Tests run: 15, Failures: 0, Errors: 0, Skipped: 0' "$harness" \
  || fail "exact LinuxSecurityIT result gate is missing"

echo "Runner Docker acceptance configuration checks passed."
