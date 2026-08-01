#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging-common.sh"

validate_staging_env
[[ -n "$("${compose[@]}" ps --status running -q frontend)" ]] || die "staging frontend is not running"
"${compose[@]}" --profile tools run --rm --no-deps smoke
