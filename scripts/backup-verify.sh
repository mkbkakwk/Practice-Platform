#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/backup-lib.sh"
backup_dir=
expected_backup_tool_git_sha=
expected_production_runtime_git_sha=
while [[ $# -gt 0 ]]; do
  case "$1" in
    --expect-backup-tool-git-sha) expected_backup_tool_git_sha="${2:-}"; shift 2;;
    --expect-production-runtime-git-sha) expected_production_runtime_git_sha="${2:-}"; shift 2;;
    *) [[ -z "$backup_dir" ]] || { echo "usage: backup-verify.sh BACKUP_DIRECTORY [--expect-backup-tool-git-sha SHA] [--expect-production-runtime-git-sha SHA]" >&2; exit 2; }; backup_dir="$1"; shift;;
  esac
done
[[ -n "$backup_dir" ]] || { echo "usage: backup-verify.sh BACKUP_DIRECTORY [--expect-backup-tool-git-sha SHA] [--expect-production-runtime-git-sha SHA]" >&2; exit 2; }
backup_verify_dir "$backup_dir"
manifest="$backup_dir/manifest.json"
format="$(sed -n 's/.*"formatVersion"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$manifest" | head -n 1)"
if [[ -n "$expected_backup_tool_git_sha" || -n "$expected_production_runtime_git_sha" ]]; then
  [[ "$format" == 2 ]] || backup_die "provenance expectations require manifest format 2"
fi
if [[ -n "$expected_backup_tool_git_sha" ]]; then
  backup_is_full_sha "$expected_backup_tool_git_sha" || backup_die "expected backup-tool Git SHA is invalid"
  [[ "$(backup_manifest_value "$manifest" backupToolGitSha)" == "$expected_backup_tool_git_sha" ]] \
    || backup_die "manifest backup-tool Git SHA does not match expected value"
fi
if [[ -n "$expected_production_runtime_git_sha" ]]; then
  backup_is_full_sha "$expected_production_runtime_git_sha" || backup_die "expected production runtime Git SHA is invalid"
  [[ "$(backup_manifest_value "$manifest" productionRuntimeGitSha)" == "$expected_production_runtime_git_sha" ]] \
    || backup_die "manifest production runtime Git SHA does not match expected value"
fi
backup_note verify "PASS: $backup_dir"
