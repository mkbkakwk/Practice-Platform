#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/backup-lib.sh"
[[ $# -eq 1 ]] || { echo "usage: backup-verify.sh BACKUP_DIRECTORY" >&2; exit 2; }
backup_verify_dir "$1"
backup_note verify "PASS: $1"
