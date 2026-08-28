#!/usr/bin/env bash

set -u

backup_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backup_repo_root="$(cd "$backup_lib_dir/.." && pwd)"

backup_die() { echo "ERROR: $*" >&2; exit 1; }
backup_note() { echo "[$1] ${*:2}"; }
backup_require() { command -v "$1" >/dev/null 2>&1 || backup_die "$1 is required"; }

backup_realpath() { realpath -m "$1"; }

backup_docker_host_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -aw "$1"
  else
    backup_realpath "$1"
  fi
}

backup_assert_root() {
  local root="$1" root_real repo_real
  [[ -n "$root" && "$root" != / && "$root" != . && "$root" != .. ]] || backup_die "BACKUP_ROOT must be an explicit safe host directory"
  mkdir -p "$root" || backup_die "cannot create backup root"
  root_real="$(backup_realpath "$root")"
  repo_real="$(backup_realpath "$backup_repo_root")"
  [[ "$root_real" != "$repo_real" && "$root_real" != "$repo_real"/* ]] || backup_die "backup root must be outside the Git repository"
  [[ ! -L "$root_real" ]] || backup_die "backup root must not be a symlink"
  printf '%s\n' "$root_real"
}

backup_assert_volume() {
  local volume="$1"
  [[ "$volume" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]+$ ]] || backup_die "invalid Office volume name"
  docker volume inspect "$volume" >/dev/null 2>&1 || backup_die "Office volume does not exist: $volume"
}

backup_manifest_value() {
  local manifest="$1" key="$2"
  sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$manifest" | head -n 1
}

backup_is_full_sha() { [[ "$1" =~ ^[0-9a-f]{40}$ ]]; }
backup_is_utc_time() { [[ "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{6}Z$ ]]; }

backup_verify_dir() {
  local dir="$1" manifest mode sha created
  [[ -d "$dir" && ! -L "$dir" ]] || backup_die "backup directory is missing or unsafe"
  [[ -f "$dir/.complete" && -f "$dir/manifest.json" && -f "$dir/SHA256SUMS" ]] || backup_die "backup is incomplete"
  [[ -f "$dir/database.dump" && -f "$dir/office.tar.gz" ]] || backup_die "backup artifacts are missing"
  manifest="$dir/manifest.json"
  grep -Eq '"formatVersion"[[:space:]]*:[[:space:]]*1' "$manifest" || backup_die "unsupported manifest format"
  mode="$(backup_manifest_value "$manifest" mode)"
  [[ "$mode" == daily || "$mode" == consistent || "$mode" == weekly || "$mode" == monthly ]] || backup_die "invalid backup mode"
  sha="$(backup_manifest_value "$manifest" gitSha)"
  backup_is_full_sha "$sha" || backup_die "manifest Git SHA is invalid"
  created="$(backup_manifest_value "$manifest" createdAt)"
  backup_is_utc_time "$created" || backup_die "manifest timestamp is invalid"
  (cd "$dir" && sha256sum -c SHA256SUMS --status) || backup_die "backup checksum verification failed"
  backup_validate_archive "$dir/office.tar.gz"
}

backup_validate_archive() {
  local archive="$1" entries details
  entries="$(tar -tzf "$archive")" || backup_die "Office archive cannot be read"
  if printf '%s\n' "$entries" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
    backup_die "Office archive contains an unsafe path"
  fi
  details="$(tar -tvzf "$archive")" || backup_die "Office archive cannot be inspected"
  if printf '%s\n' "$details" | grep -Eq '^[lh]'; then
    backup_die "Office archive contains a link"
  fi
}

backup_free_bytes() {
  df -Pk "$1" | awk 'NR == 2 { printf "%.0f\n", $4 * 1024 }'
}

backup_assert_project_container() {
  local project="$1" container="$2" actual
  [[ "$project" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]+$ ]] || backup_die "invalid Compose project"
  actual="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$container" 2>/dev/null)" || backup_die "database container is unavailable"
  [[ "$actual" == "$project" ]] || backup_die "database container does not belong to Compose project $project"
}
