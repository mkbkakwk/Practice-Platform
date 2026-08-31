#!/usr/bin/env bash

# Deploys only an explicitly named, already-existing source revision into the
# isolated Staging project.  It never touches Production and never resets the
# caller's checkout.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"

usage() {
  echo "usage: staging-deploy-sha.sh --sha FULL_SHA [--env-file PATH] [--dry-run]" >&2
  exit 2
}

sha=""
env_file="$repo_root/.env.staging"
dry_run=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --sha) sha="${2:-}"; shift 2;;
    --env-file) env_file="${2:-}"; shift 2;;
    --dry-run) dry_run=true; shift;;
    *) usage;;
  esac
done

[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo "ERROR: --sha must be an exact 40-character lowercase Git SHA" >&2; exit 2; }
git -C "$repo_root" cat-file -e "$sha^{commit}" || { echo "ERROR: requested SHA is not available locally" >&2; exit 1; }
[[ -f "$env_file" ]] || { echo "ERROR: staging environment file is missing" >&2; exit 1; }

if [[ "$dry_run" == true ]]; then
  printf 'DRY-RUN: would deploy exact Staging source SHA %s using %s\n' "$sha" "$env_file"
  exit 0
fi

worktree="$(mktemp -d "${TMPDIR:-/tmp}/practice-platform-stage9d-deploy.XXXXXX")"
added=false
cleanup() {
  if [[ "$added" == true ]]; then
    git -C "$repo_root" worktree remove "$worktree" >/dev/null 2>&1 \
      || echo "WARN: temporary deploy worktree requires manual removal: $worktree" >&2
  else
    rmdir "$worktree" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

git -C "$repo_root" worktree add --detach "$worktree" "$sha" >/dev/null
added=true
STAGING_ENV_FILE="$env_file" "$worktree/scripts/staging-up.sh"
