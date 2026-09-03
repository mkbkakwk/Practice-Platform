#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/release-metadata.sh"

full_sha="0123456789abcdef0123456789abcdef01234567"
short_sha="${full_sha:0:7}"
build_time="2026-08-28T07:30:00Z"

metadata_is_full_git_sha "$full_sha" \
  || { echo "RELEASE METADATA TEST FAILED: full SHA was rejected" >&2; exit 1; }
! metadata_is_full_git_sha "$short_sha" \
  || { echo "RELEASE METADATA TEST FAILED: short image tag was accepted as a release SHA" >&2; exit 1; }
metadata_is_utc_build_time "$build_time" \
  || { echo "RELEASE METADATA TEST FAILED: UTC build timestamp was rejected" >&2; exit 1; }
! metadata_is_utc_build_time "unknown" \
  || { echo "RELEASE METADATA TEST FAILED: unknown build timestamp was accepted" >&2; exit 1; }

echo "Release metadata validation checks passed (full SHA and immutable UTC build time)."
