#!/usr/bin/env bash

set -u

release_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release_repo_root="$(cd "$release_script_dir/.." && pwd)"
release_compose_file="$release_repo_root/docker-compose.release.yml"
release_env_file="${RELEASE_ENV_FILE:-$release_repo_root/deploy/releases/v0.4.0-foundation.env}"
formal_env_file="${FORMAL_ENV_FILE:-$release_repo_root/.env}"

release_die() {
  echo "ERROR: $*" >&2
  exit 1
}

release_require_command() {
  command -v "$1" >/dev/null 2>&1 || release_die "$1 is required"
}

release_env_value() {
  local file="$1"
  local key="$2"
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      value = $0
    }
    END { print value }
  ' "$file" | tr -d '\r'
}

release_require_env_key() {
  local file="$1"
  local key="$2"
  local value
  value="$(release_env_value "$file" "$key")"
  [[ -n "$value" ]] || release_die "$key is missing from $(basename "$file")"
}

release_require_file() {
  local path="$1"
  local label="$2"
  [[ -f "$path" ]] || release_die "$label is missing: $path"
}

release_container_state() {
  docker inspect --format '{{.State.Status}}' "$1" 2>/dev/null
}

release_container_health() {
  local state_json health
  state_json="$(docker inspect --format '{{json .State}}' "$1" 2>/dev/null)" || return 1
  health="$(printf '%s' "$state_json" | sed -n 's/.*"Health":{"Status":"\([^"]*\)".*/\1/p')"
  printf '%s\n' "${health:-not-configured}"
}
