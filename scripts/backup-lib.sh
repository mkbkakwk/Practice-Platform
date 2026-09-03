#!/usr/bin/env bash

set -u

backup_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backup_repo_root="$(cd "$backup_lib_dir/.." && pwd)"

backup_die() { echo "ERROR: $*" >&2; exit 1; }
backup_note() { echo "[$1] ${*:2}"; }
backup_require() { command -v "$1" >/dev/null 2>&1 || backup_die "$1 is required"; }

backup_realpath() { realpath -m "$1"; }

backup_is_windows_posix_shell() {
  case "${OSTYPE:-}:${MSYSTEM:-}" in
    msys*:*|cygwin*:*|*:MINGW*|*:MSYS*) return 0 ;;
    *) return 1 ;;
  esac
}

backup_is_unc_path() {
  case "$1" in
    //*) return 0 ;;
    \\\\*) return 0 ;;
    *) return 1 ;;
  esac
}

backup_is_windows_drive_root() {
  [[ "$1" =~ ^[A-Za-z]:/$ || "$1" =~ ^/[A-Za-z]/?$ ]]
}

backup_shell_path() {
  local path="$1" resolved
  [[ -n "$path" ]] || backup_die "backup path must not be empty"
  backup_is_unc_path "$path" && backup_die "UNC backup roots are not supported"
  resolved="$(backup_realpath "$path")"
  if backup_is_windows_posix_shell; then
    command -v cygpath >/dev/null 2>&1 || backup_die "cygpath is required for Windows backup paths"
    cygpath -au "$resolved"
  else
    printf '%s\n' "$resolved"
  fi
}

backup_docker_host_path() {
  local path="$1" shell_path
  [[ -n "$path" ]] || backup_die "Docker bind source must not be empty"
  backup_is_unc_path "$path" && backup_die "UNC backup roots are not supported"
  shell_path="$(backup_shell_path "$path")"
  if backup_is_windows_posix_shell; then
    command -v cygpath >/dev/null 2>&1 || backup_die "cygpath is required for Windows Docker bind paths"
    # Docker Desktop accepts mixed drive paths (C:/...) in --mount source.
    # Keep MSYS conversion disabled at the call site so this is passed verbatim.
    cygpath -am "$shell_path"
  else
    printf '%s\n' "$shell_path"
  fi
}

backup_assert_root() {
  local root="$1" root_real root_shell repo_real
  [[ -n "$root" && "$root" != / && "$root" != . && "$root" != .. ]] || backup_die "BACKUP_ROOT must be an explicit safe host directory"
  backup_is_unc_path "$root" && backup_die "UNC backup roots are not supported"
  root_real="$(backup_realpath "$root")"
  backup_is_windows_posix_shell && backup_is_windows_drive_root "$root_real" && backup_die "backup root must not be a Windows drive root"
  root_shell="$(backup_shell_path "$root_real")"
  mkdir -p "$root_shell" || backup_die "cannot create backup root"
  repo_real="$(backup_realpath "$backup_repo_root")"
  [[ "$root_real" != "$repo_real" && "$root_real" != "$repo_real"/* ]] || backup_die "backup root must be outside the Git repository"
  [[ ! -L "$root_real" ]] || backup_die "backup root must not be a symlink"
  printf '%s\n' "$root_shell"
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

# New backups are created below a private temporary directory.  Keep the
# permission check next to the chmod so callers cannot accidentally apply it
# to an arbitrary path (or to an existing, possibly historical backup).
backup_restrict_temp_dir() {
  local dir="$1" mode
  dir="$(backup_shell_path "$dir")"
  [[ -d "$dir" && ! -L "$dir" ]] || backup_die "backup temporary directory is missing or unsafe"
  if ! backup_is_windows_posix_shell; then
    chmod 700 "$dir" || backup_die "cannot restrict backup temporary directory permissions"
    mode="$(stat -c %a "$dir")" || backup_die "cannot read backup temporary directory permissions"
    [[ "$mode" == 700 ]] || backup_die "backup temporary directory permissions are not restrictive"
  fi
}

backup_restrict_artifact() {
  local dir="$1" name="$2" artifact mode
  dir="$(backup_shell_path "$dir")"
  [[ "$name" != */* && "$name" != . && "$name" != .. && -n "$name" ]] || backup_die "invalid backup artifact name"
  [[ -d "$dir" && ! -L "$dir" ]] || backup_die "backup temporary directory is missing or unsafe"
  artifact="$dir/$name"
  [[ -f "$artifact" && ! -L "$artifact" ]] || backup_die "backup artifact is missing or unsafe: $name"
  if ! backup_is_windows_posix_shell; then
    chmod 600 "$artifact" || backup_die "cannot restrict backup artifact permissions: $name"
    mode="$(stat -c %a "$artifact")" || backup_die "cannot read backup artifact permissions: $name"
    [[ "$mode" == 600 ]] || backup_die "backup artifact permissions are not restrictive: $name"
  fi
}

backup_verify_dir() {
  local dir="$1" manifest mode sha created
  dir="$(backup_shell_path "$dir")"
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
  archive="$(backup_shell_path "$archive")"
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
  local filesystem blocks used available capacity mount
  read -r filesystem blocks used available capacity mount < <(df -Pk "$1" | tail -n 1)
  [[ "$available" =~ ^[0-9]+$ ]] || return 1
  printf '%s\n' "$((available * 1024))"
}

backup_assert_project_container() {
  local project="$1" container="$2" actual
  [[ "$project" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]+$ ]] || backup_die "invalid Compose project"
  actual="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$container" 2>/dev/null)" || backup_die "database container is unavailable"
  [[ "$actual" == "$project" ]] || backup_die "database container does not belong to Compose project $project"
}
