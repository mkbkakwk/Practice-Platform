#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
env_file="${STAGING_ENV_FILE:-$repo_root/.env.staging}"

command -v docker >/dev/null 2>&1 || {
  echo "ERROR: docker is required" >&2
  exit 127
}
command -v git >/dev/null 2>&1 || {
  echo "ERROR: git is required" >&2
  exit 127
}
[[ ! -e "$env_file" ]] || {
  echo "ERROR: $env_file already exists; refusing to overwrite it" >&2
  exit 1
}

random_hex() {
  local bytes="$1"
  docker run --rm alpine:3.20 sh -c "od -An -N$bytes -tx1 /dev/urandom | tr -d ' \n'"
}

postgres_password="$(random_hex 24)"
rabbitmq_password="$(random_hex 24)"
jwt_secret="$(random_hex 48)"
smoke_password="$(random_hex 24)"
git_sha="$(git -C "$repo_root" rev-parse --short=7 HEAD)"
full_git_sha="$(git -C "$repo_root" rev-parse HEAD)"
build_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

umask 077
{
  printf '%s\n' "STAGING_FRONTEND_PORT=18080"
  printf '%s\n' "STAGING_POSTGRES_DB=practice_platform_staging"
  printf '%s\n' "STAGING_POSTGRES_USER=staging_user"
  printf '%s\n' "STAGING_POSTGRES_PASSWORD=$postgres_password"
  printf '%s\n' "STAGING_RABBITMQ_USER=staging_user"
  printf '%s\n' "STAGING_RABBITMQ_PASSWORD=$rabbitmq_password"
  printf '%s\n' "STAGING_JWT_SECRET=$jwt_secret"
  printf '%s\n' "STAGING_JWT_EXPIRES_IN=1d"
  printf '%s\n' "STAGING_PROMOTE_FIRST_ADMIN=false"
  printf '%s\n' "STAGING_WORKER_CONCURRENCY=1"
  printf '%s\n' "STAGING_WORKER_MAX_CONCURRENCY=1"
  printf '%s\n' "STAGING_SMOKE_USERNAME_PREFIX=stgsmoke"
  printf '%s\n' "STAGING_SMOKE_PASSWORD=$smoke_password"
  printf '%s\n' "STAGING_GIT_SHA=$git_sha"
  printf '%s\n' "STAGING_FULL_GIT_SHA=$full_git_sha"
  printf '%s\n' "STAGING_BUILD_TIME=$build_time"
} > "$env_file"

chmod 600 "$env_file" 2>/dev/null || true
echo "Created $env_file with staging-only random credentials (values hidden)."
