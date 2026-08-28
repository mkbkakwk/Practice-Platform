#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/backup-lib.sh"
usage() { echo "usage: backup-retention.sh --backup-root DIR [--dry-run|--apply]" >&2; exit 2; }
root= action=dry-run
while [[ $# -gt 0 ]]; do case "$1" in --backup-root) root="${2:-}"; shift 2;; --dry-run) action=dry-run; shift;; --apply) action=apply; shift;; *) usage;; esac; done
[[ -n "$root" ]] || usage
root="$(backup_assert_root "$root")"
retain_category() {
  local category="$1" keep="$2" dir created safe_category
  safe_category="$root/$category"; [[ -d "$safe_category" && ! -L "$safe_category" ]] || return 0
  local -a ordered=()
  while IFS= read -r -d '' dir; do
    if ! backup_verify_dir "$dir" >/dev/null 2>&1; then echo "CORRUPT $dir" >&2; continue; fi
    created="$(backup_manifest_value "$dir/manifest.json" createdAt)"
    ordered+=("$created|$dir")
  done < <(find "$safe_category" -mindepth 1 -maxdepth 1 -type d -print0 | sort -z)
  mapfile -t ordered < <(printf '%s\n' "${ordered[@]}" | sed '/^$/d' | sort -r)
  local i entry candidate candidate_real category_real
  category_real="$(backup_realpath "$safe_category")"
  for ((i=0; i<${#ordered[@]}; i++)); do
    entry="${ordered[$i]}"; candidate="${entry#*|}"; candidate_real="$(backup_realpath "$candidate")"
    [[ "$candidate_real" == "$category_real"/* && "$candidate_real" != "$category_real" ]] || backup_die "retention path escaped backup category"
    if (( i < keep )); then echo "KEEP $candidate"; else
      echo "DELETE $candidate"
      if [[ "$action" == apply ]]; then rm -rf -- "$candidate"; fi
    fi
  done
}
retain_category daily 7
retain_category weekly 4
retain_category monthly 3
