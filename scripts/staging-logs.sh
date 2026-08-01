#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging-common.sh"

tail_lines="${STAGING_LOG_TAIL:-200}"
[[ "$tail_lines" =~ ^[0-9]+$ ]] || die "STAGING_LOG_TAIL must be numeric"
exec "${compose[@]}" logs --tail "$tail_lines" "$@"
