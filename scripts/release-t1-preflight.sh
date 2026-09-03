#!/usr/bin/env bash

# Read-only gate for the authoritative pre-release backup.  This intentionally
# runs while Production still uses the previous application/runtime revision.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/release-common.sh"
source "$script_dir/release-metadata.sh"

release_require_command docker
release_require_command git
release_require_file "$release_env_file" "local release metadata"
release_require_file "$formal_env_file" "production environment file"

for key in PREVIOUS_PRODUCTION_GIT_SHA PREVIOUS_PRODUCTION_FLYWAY_VERSION \
  BACKUP_TOOL_GIT_SHA T1_PRODUCTION_RUNTIME_GIT_SHA FORMAL_BACKEND_CONTAINER FORMAL_POSTGRES_CONTAINER; do
  release_require_env_key "$release_env_file" "$key"
done

previous_sha="$(release_env_value "$release_env_file" PREVIOUS_PRODUCTION_GIT_SHA)"
declared_sha="$(release_env_value "$release_env_file" T1_PRODUCTION_RUNTIME_GIT_SHA)"
tool_sha="$(release_env_value "$release_env_file" BACKUP_TOOL_GIT_SHA)"
expected_flyway="$(release_env_value "$release_env_file" PREVIOUS_PRODUCTION_FLYWAY_VERSION)"
for sha in "$previous_sha" "$declared_sha" "$tool_sha"; do
  metadata_is_full_git_sha "$sha" || release_die "backup provenance SHA must be full lowercase hexadecimal"
  git -C "$release_repo_root" cat-file -e "$sha^{commit}" 2>/dev/null \
    || release_die "backup provenance SHA does not exist locally"
done
actual_tool_sha="$(git -C "$release_repo_root" rev-parse HEAD)"
[[ "$tool_sha" == "$actual_tool_sha" ]] || release_die "BACKUP_TOOL_GIT_SHA does not match this backup-tool checkout"

backend_container="$(release_env_value "$release_env_file" FORMAL_BACKEND_CONTAINER)"
db_container="$(release_env_value "$release_env_file" FORMAL_POSTGRES_CONTAINER)"
observed_sha="$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$backend_container" 2>/dev/null)"
metadata_require_matching_production_runtime "$previous_sha" "$observed_sha" "$declared_sha" \
  || release_die "expected, observed, and declared T1 production runtime SHAs must match"

flyway="$(docker exec -e PGOPTIONS='-c default_transaction_read_only=on' "$db_container" \
  sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"')"
[[ "$flyway" == "$expected_flyway" ]] || release_die "Flyway version is $flyway, expected $expected_flyway before T1 backup"

echo "T1 backup provenance preflight passed:"
echo "  backup tool: $tool_sha"
echo "  production runtime: $observed_sha"
echo "  Flyway: V$flyway"
echo "No Production resources were modified."
