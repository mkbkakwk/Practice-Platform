#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/backup-lib.sh"
usage() { echo "usage: restore.sh --backup DIR --target isolated --confirm-isolated --project practice-platform-stage9b-test-* --db-container NAME --db-name NAME --db-user NAME --office-volume NAME" >&2; exit 2; }
backup= target= confirm= project= db_container= db_name= db_user= office_volume=
while [[ $# -gt 0 ]]; do case "$1" in --backup) backup="${2:-}"; shift 2;; --target) target="${2:-}"; shift 2;; --confirm-isolated) confirm=yes; shift;; --project) project="${2:-}"; shift 2;; --db-container) db_container="${2:-}"; shift 2;; --db-name) db_name="${2:-}"; shift 2;; --db-user) db_user="${2:-}"; shift 2;; --office-volume) office_volume="${2:-}"; shift 2;; *) usage;; esac; done
[[ "$target" == isolated && "$confirm" == yes && "$project" == practice-platform-stage9b-test-* ]] || backup_die "restore is restricted to an explicitly confirmed Stage 9B isolated target"
[[ -n "$backup" && -n "$db_container" && -n "$db_name" && -n "$db_user" && -n "$office_volume" ]] || usage
backup_assert_project_container "$project" "$db_container"; backup_assert_volume "$office_volume"; backup_verify_dir "$backup"
table_count="$(docker exec "$db_container" psql -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" | tr -d '\r\n')"
[[ "$table_count" == 0 ]] || backup_die "restore target database is not empty"
target_entries="$(docker run --rm -v "$office_volume:/target" postgres:16-alpine sh -c 'find /target -mindepth 1 -print -quit')"
[[ -z "$target_entries" ]] || backup_die "restore target Office volume is not empty"
backup_note restore "restoring PostgreSQL custom dump to isolated target"
docker exec -i "$db_container" pg_restore -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" --no-owner --no-privileges --exit-on-error < "$backup/database.dump"
backup_note restore "restoring Office archive to isolated volume"
backup_mount="$(backup_docker_host_path "$backup")"
MSYS_NO_PATHCONV=1 docker run --rm -v "$office_volume:/target" --mount "type=bind,src=$backup_mount,dst=/backup,readonly" postgres:16-alpine sh -ec '
  tar -tzf /backup/office.tar.gz | grep -Eq "(^/|(^|/)\\.\\.(/|$))" && exit 1 || true
  tar -tvzf /backup/office.tar.gz | grep -Eq "^[lh]" && exit 1 || true
  tar -C /target -xzf /backup/office.tar.gz
  test -z "$(find /target -type l -print -quit)"
'
backup_note restore "complete"
