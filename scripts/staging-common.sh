#!/usr/bin/env bash

set -u

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
project_name="practice-platform-staging"
env_file="${STAGING_ENV_FILE:-$repo_root/.env.staging}"
compose_file="$repo_root/docker-compose.staging.yml"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required"
}

env_value() {
  local key="$1"
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      value = $0
    }
    END { print value }
  ' "$env_file" | tr -d '\r'
}

validate_staging_env() {
  local key value lowered
  for key in     STAGING_POSTGRES_DB     STAGING_POSTGRES_USER     STAGING_POSTGRES_PASSWORD     STAGING_RABBITMQ_USER     STAGING_RABBITMQ_PASSWORD     STAGING_JWT_SECRET     STAGING_SMOKE_PASSWORD
  do
    value="$(env_value "$key")"
    [[ -n "$value" ]] || die "$key is missing from .env.staging"
    lowered="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
    case "$lowered" in
      change-me*|replace-with*|please-change*)
        die "$key still contains a placeholder"
        ;;
    esac
  done

  value="$(env_value STAGING_JWT_SECRET)"
  [[ ${#value} -ge 32 ]] || die "STAGING_JWT_SECRET must contain at least 32 characters"

  value="$(env_value STAGING_PROMOTE_FIRST_ADMIN)"
  case "$value" in
    false|FALSE|0) ;;
    *) die "STAGING_PROMOTE_FIRST_ADMIN must remain false" ;;
  esac

  value="$(env_value STAGING_FRONTEND_PORT)"
  [[ "$value" =~ ^[0-9]+$ ]] || die "STAGING_FRONTEND_PORT must be numeric"
  (( value >= 1024 && value <= 65535 )) || die "STAGING_FRONTEND_PORT is outside 1024-65535"
}

port_is_available() {
  local port="$1"
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -NonInteractive -Command "\$listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue; if (\$listener) { exit 1 }" >/dev/null
    return $?
  fi
  if command -v ss >/dev/null 2>&1; then
    ! ss -ltn | grep -Eq "[:.]$port[[:space:]]"
    return $?
  fi
  if command -v netstat >/dev/null 2>&1; then
    ! netstat -an | grep -E "[:.]$port[[:space:]].*LISTEN" >/dev/null
    return $?
  fi
  die "cannot verify whether port $port is available"
}

assert_safe_staging_volumes() {
  local volume
  for volume in     practice-platform-staging-pgdata     practice-platform-staging-rabbitmq     practice-platform-staging-docs
  do
    case "$volume" in
      *staging*) ;;
      *) die "refusing to delete non-staging volume: $volume" ;;
    esac
  done
}

require_command docker
require_command git
docker compose version >/dev/null 2>&1 || die "docker compose is required"
[[ -f "$env_file" ]] || die ".env.staging is missing; run ./scripts/staging-init-env.sh first"
[[ -f "$compose_file" ]] || die "docker-compose.staging.yml is missing"

export STAGING_GIT_SHA="${STAGING_GIT_SHA:-$(git -C "$repo_root" rev-parse --short HEAD)}"
compose=(
  docker compose
  --project-name "$project_name"
  --env-file "$env_file"
  -f "$repo_root/docker-compose.yml"
  -f "$compose_file"
)
