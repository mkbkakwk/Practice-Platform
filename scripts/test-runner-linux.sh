#!/usr/bin/env bash

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! "$repo_root/scripts/runner-linux-preflight.sh"; then
  echo "Linux isolation tests: NOT RUN" >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: Maven is required on the dedicated Linux acceptance host" >&2
  echo "Linux isolation tests: NOT RUN" >&2
  exit 127
fi

mvn -B -f "$repo_root/runner/pom.xml" -Plinux-security verify
rc=$?
if [[ $rc -ne 0 ]]; then
  echo "Linux isolation tests: FAILED" >&2
  exit "$rc"
fi

echo "Linux isolation tests: PASSED"
