#!/usr/bin/env bash

set -euo pipefail
umask 077
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/backup-lib.sh"

usage() { echo "usage: backup.sh --environment test|staging|release --mode daily|consistent|weekly|monthly --backup-root DIR --project NAME --db-container NAME --db-name NAME --db-user NAME --office-volume NAME [--compose-file FILE]" >&2; exit 2; }
environment= mode= root= project= db_container= db_name= db_user= office_volume=
compose_files=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) environment="${2:-}"; shift 2;; --mode) mode="${2:-}"; shift 2;; --backup-root) root="${2:-}"; shift 2;;
    --project) project="${2:-}"; shift 2;; --db-container) db_container="${2:-}"; shift 2;; --db-name) db_name="${2:-}"; shift 2;;
    --db-user) db_user="${2:-}"; shift 2;; --office-volume) office_volume="${2:-}"; shift 2;; --compose-file) compose_files+=("${2:-}"); shift 2;;
    *) usage;;
  esac
done
[[ "$environment" == test || "$environment" == staging || "$environment" == release ]] || usage
[[ "$mode" == daily || "$mode" == consistent || "$mode" == weekly || "$mode" == monthly ]] || usage
[[ -n "$root" && -n "$project" && -n "$db_container" && -n "$db_name" && -n "$db_user" && -n "$office_volume" ]] || usage
for command in docker git tar sha256sum df; do backup_require "$command"; done
backup_assert_project_container "$project" "$db_container"
backup_assert_volume "$office_volume"
root="$(backup_assert_root "$root")"
minimum_free="${BACKUP_MIN_FREE_BYTES:-1073741824}"
[[ "$minimum_free" =~ ^[0-9]+$ ]] || backup_die "BACKUP_MIN_FREE_BYTES must be numeric"
available="$(backup_free_bytes "$root")"
[[ "$available" =~ ^[0-9]+$ && "$available" -ge "$minimum_free" ]] || backup_die "insufficient free space before backup"

git_sha="$(git -C "$backup_repo_root" rev-parse HEAD)"
backup_is_full_sha "$git_sha" || backup_die "cannot determine full source Git SHA"
created_at="$(date -u +%Y-%m-%dT%H%M%SZ)"
backup_id="${created_at}_${git_sha}"
category="$root/$mode"
mkdir -p "$category"
tmp="$(mktemp -d "$category/.${backup_id}.tmp.XXXXXX")"
final="$category/$backup_id"
cleanup_tmp() { if [[ -n "${tmp:-}" && -d "$tmp" ]]; then rm -rf -- "$tmp"; fi; return 0; }
quiesced=0
compose=(docker compose -p "$project")
for file in "${compose_files[@]}"; do compose+=( -f "$file" ); done
restore_services() { if [[ "$quiesced" == 1 ]]; then "${compose[@]}" start backend worker >/dev/null || echo "WARN: unable to restart quiesced services" >&2; fi; }
trap 'restore_services; cleanup_tmp' EXIT INT TERM

if [[ "$mode" != daily ]]; then
  ((${#compose_files[@]} > 0)) || backup_die "consistent mode requires explicit Compose files"
  backup_note backup "quiescing Backend and Worker write paths"
  "${compose[@]}" stop backend worker
  quiesced=1
fi

flyway_version="$(docker exec "$db_container" psql -U "$db_user" -d "$db_name" -Atc 'SELECT max(version) FROM flyway_schema_history WHERE success;' | tr -d '\r\n')"
[[ "$flyway_version" =~ ^[0-9]+$ ]] || backup_die "unable to determine Flyway version"
backup_note backup "writing PostgreSQL custom-format dump"
docker exec "$db_container" pg_dump -U "$db_user" -d "$db_name" -Fc > "$tmp/database.dump"
[[ -s "$tmp/database.dump" ]] || backup_die "database dump is empty"
backup_restrict_artifact "$tmp" database.dump

backup_note backup "archiving Office persistent storage"
tmp_mount="$(backup_docker_host_path "$tmp")"
MSYS_NO_PATHCONV=1 docker run --rm -v "$office_volume:/source:ro" --mount "type=bind,src=$tmp_mount,dst=/out" postgres:16-alpine sh -ec '
  umask 077
  test -z "$(find /source -type l -print -quit)" || { echo "Office storage contains a symlink" >&2; exit 1; }
  tar -C /source -czf /out/office.tar.gz .
'
[[ -s "$tmp/office.tar.gz" ]] || backup_die "Office archive is empty"
backup_validate_archive "$tmp/office.tar.gz"
backup_restrict_artifact "$tmp" office.tar.gz

cat > "$tmp/manifest.json" <<EOF
{
  "formatVersion": 1,
  "backupId": "$backup_id",
  "createdAt": "$created_at",
  "mode": "$mode",
  "gitSha": "$git_sha",
  "flywayVersion": "$flyway_version",
  "database": { "format": "pg_custom", "file": "database.dump" },
  "office": { "format": "tar.gz", "file": "office.tar.gz" },
  "rabbitmq": { "authoritative": false }
}
EOF
backup_restrict_artifact "$tmp" manifest.json
(cd "$tmp" && sha256sum database.dump office.tar.gz manifest.json > SHA256SUMS)
backup_restrict_artifact "$tmp" SHA256SUMS
touch "$tmp/.complete"
backup_restrict_artifact "$tmp" .complete
backup_restrict_temp_dir "$tmp"
backup_verify_dir "$tmp"
mv "$tmp" "$final"
tmp=
backup_note backup "complete: $final"
