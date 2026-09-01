#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
project="practice-platform-stage9b-test-${RANDOM}${RANDOM}"
source_db="${project}-source-db"
target_db="${project}-target-db"
source_pg="${project}-source-pgdata"
target_pg="${project}-target-pgdata"
source_office="${project}-source-office"
target_office="${project}-target-office"
source "$script_dir/backup-lib.sh"
backup_parent="${STAGE9B_TEST_BACKUP_PARENT:-${TMPDIR:-/tmp}}"
backup_parent="$(backup_shell_path "$backup_parent")"
mkdir -p "$backup_parent"
backup_root="$(backup_shell_path "$(mktemp -d "$backup_parent/practice-platform-stage9b-backups.XXXXXX")")"
consistent_compose="$repo_root/test/fixtures/stage9b/consistent-compose.yml"
db_user=stage9b
db_name=stage9b

fail() { echo "STAGE 9B TEST FAILED: $*" >&2; exit 1; }
assert_backup_permissions() {
  local dir="$1" mode file file_mode
  mode="$(stat -c %a "$dir")"
  (( (8#$mode & 2) == 0 )) || fail "backup directory is world-writable"
  if ! backup_is_windows_posix_shell; then
    [[ "$mode" == 700 ]] || fail "Linux backup directory mode must be 700, got $mode"
  fi
  for file in database.dump office.tar.gz manifest.json SHA256SUMS .complete; do
    file_mode="$(stat -c %a "$dir/$file")"
    (( (8#$file_mode & 2) == 0 )) || fail "backup artifact $file is world-writable"
    if ! backup_is_windows_posix_shell; then
      [[ "$file_mode" == 600 ]] || fail "Linux backup artifact $file mode must be 600, got $file_mode"
    fi
  done
}
assert_archive_helper_permissions() {
  local dir="$1" mode
  # office.tar.gz is created by the production archive-helper container,
  # not by the host shell.  This directly guards its independent umask.
  mode="$(stat -c %a "$dir/office.tar.gz")"
  if ! backup_is_windows_posix_shell; then
    [[ "$mode" == 600 ]] || fail "Linux archive-helper office.tar.gz mode must be 600, got $mode"
  fi
}
diagnose_pg() {
  local container="$1"
  echo "PostgreSQL readiness timeout for isolated target: $container" >&2
  docker inspect --format 'status={{.State.Status}} running={{.State.Running}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" >&2 || true
  docker logs --tail 40 "$container" >&2 || true
}
cleanup() {
  docker compose -p "$project" -f "$consistent_compose" down --remove-orphans >/dev/null 2>&1 || true
  docker rm -f "$source_db" "$target_db" >/dev/null 2>&1 || true
  docker volume rm "$source_pg" "$target_pg" "$source_office" "$target_office" >/dev/null 2>&1 || true
  rm -rf -- "$backup_root"
}
trap cleanup EXIT INT TERM
wait_pg() {
  local container="$1" tries=0
  until docker exec "$container" pg_isready -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" >/dev/null 2>&1 \
    && docker exec "$container" psql -h 127.0.0.1 -p 5432 -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" -Atc 'SELECT 1' >/dev/null 2>&1; do
    ((tries+=1))
    if ((tries >= 40)); then
      diagnose_pg "$container"
      fail "PostgreSQL did not become ready within 40 seconds"
    fi
    sleep 1
  done
}
create_db() {
  local container="$1" volume="$2"
  docker volume create --label "com.docker.compose.project=$project" "$volume" >/dev/null
  docker run -d --name "$container" --label "com.docker.compose.project=$project" \
    -e POSTGRES_USER="$db_user" -e POSTGRES_PASSWORD=stage9b-test-password -e POSTGRES_DB="$db_name" \
    -v "$volume:/var/lib/postgresql/data" postgres:16-alpine >/dev/null
  wait_pg "$container"
}

echo "==> Stage 9B verify Docker bind-path portability"
bind_probe="$backup_root/bind path probe"
mkdir -p "$bind_probe"
printf 'host-visible' > "$bind_probe/from-host.txt"
probe_mount="$(backup_docker_host_path "$bind_probe")"
MSYS_NO_PATHCONV=1 docker run --rm --mount "type=bind,src=$probe_mount,dst=/probe" postgres:16-alpine sh -ec '
  test "$(cat /probe/from-host.txt)" = host-visible
  printf container-visible > /probe/from-container.txt
'
[[ "$(cat "$bind_probe/from-container.txt")" == container-visible ]] || fail "Docker bind probe did not round-trip to host"
if backup_is_windows_posix_shell; then
  [[ "$probe_mount" =~ ^[A-Za-z]:/ ]] || fail "Windows Docker bind source was not normalized to a mixed drive path"
  probe_backslash="$(cygpath -aw "$bind_probe")"
  [[ "$(backup_docker_host_path "$probe_backslash")" == "$probe_mount" ]] || fail "Windows backslash bind source was not normalized consistently"
  for unsafe_root in 'C:/' 'C:\\' 'D:/'; do
    if (backup_assert_root "$unsafe_root" >/dev/null 2>&1); then
      fail "Windows drive root was accepted as a backup root: $unsafe_root"
    fi
  done
  if (backup_docker_host_path '//server/share' >/dev/null 2>&1); then
    fail "UNC Docker bind source was accepted"
  fi
fi
backup() {
  BACKUP_MIN_FREE_BYTES=1 "$BASH" "$script_dir/backup.sh" \
    --environment test --mode daily --backup-root "$backup_root" --project "$project" \
    --db-container "$source_db" --db-name "$db_name" --db-user "$db_user" --office-volume "$source_office"
}
consistent_backup() {
  BACKUP_MIN_FREE_BYTES=1 "$BASH" "$script_dir/backup.sh" \
    --environment test --mode consistent --backup-root "$backup_root" --project "$project" \
    --db-container "$source_db" --db-name "$db_name" --db-user "$db_user" --office-volume "$source_office" \
    --compose-file "$consistent_compose"
}

command -v docker >/dev/null || fail "docker is required"
create_db "$source_db" "$source_pg"
docker volume create --label "com.docker.compose.project=$project" "$source_office" >/dev/null
docker compose -p "$project" -f "$consistent_compose" up -d backend worker >/dev/null
docker exec -i "$source_db" psql -h 127.0.0.1 -p 5432 -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" <<'SQL'
CREATE TABLE flyway_schema_history (installed_rank integer primary key, version varchar(50), success boolean);
INSERT INTO flyway_schema_history VALUES (1, '9', true);
CREATE TABLE users (id integer primary key, username text not null);
CREATE TABLE problems (id integer primary key, title text not null);
CREATE TABLE contests (id integer primary key, title text not null);
CREATE TABLE submissions (id integer primary key, user_id integer references users(id), problem_id integer references problems(id));
CREATE TABLE judge_outbox (id integer primary key, status text not null);
CREATE TABLE office_metadata (id integer primary key, storage_id text not null);
INSERT INTO users VALUES (1, 'restore-fixture');
INSERT INTO problems VALUES (1, 'backup fixture');
INSERT INTO contests VALUES (1, 'restore contest');
INSERT INTO submissions VALUES (1, 1, 1);
INSERT INTO judge_outbox VALUES (1, 'PUBLISHED');
INSERT INTO office_metadata VALUES (1, 'nested/teacher reference.docx');
SQL
docker run --rm -v "$source_office:/office" postgres:16-alpine sh -ec '
  mkdir -p /office/nested
  printf "starter binary\003\004" > /office/starter.docx
  printf "teacher binary\003\004" > "/office/nested/teacher reference.docx"
  printf "student binary\003\004" > "/office/学生提交.docx"
'
source_hash="$(docker run --rm -v "$source_office:/office:ro" postgres:16-alpine sh -c 'sha256sum "/office/nested/teacher reference.docx" | awk "{print \$1}"')"

echo "==> Stage 9B create and verify backup"
backup
backup_dir="$(find "$backup_root/daily" -mindepth 1 -maxdepth 1 -type d -print -quit)"
[[ -n "$backup_dir" ]] || fail "backup was not published"
"$BASH" "$script_dir/backup-verify.sh" "$backup_dir"
grep -Eq '"flywayVersion"[[:space:]]*:[[:space:]]*"9"' "$backup_dir/manifest.json" || fail "Flyway metadata missing"
grep -Eq '"gitSha"[[:space:]]*:[[:space:]]*"[0-9a-f]{40}"' "$backup_dir/manifest.json" || fail "full Git SHA missing"
assert_backup_permissions "$backup_dir"
assert_archive_helper_permissions "$backup_dir"

echo "==> Stage 9B create and verify quiesced backup"
consistent_backup
consistent_dir="$(find "$backup_root/consistent" -mindepth 1 -maxdepth 1 -type d -print -quit)"
[[ -n "$consistent_dir" ]] || fail "consistent backup was not published"
"$BASH" "$script_dir/backup-verify.sh" "$consistent_dir"
assert_backup_permissions "$consistent_dir"
assert_archive_helper_permissions "$consistent_dir"
[[ "$(docker inspect --format '{{.State.Running}}' "${project}-backend-1")" == true ]] || fail "consistent backup did not restore Backend"
[[ "$(docker inspect --format '{{.State.Running}}' "${project}-worker-1")" == true ]] || fail "consistent backup did not restore Worker"

echo "==> Stage 9B reject disk exhaustion before backup"
if BACKUP_MIN_FREE_BYTES=999999999999999999 "$BASH" "$script_dir/backup.sh" --environment test --mode daily --backup-root "$backup_root" --project "$project" --db-container "$source_db" --db-name "$db_name" --db-user "$db_user" --office-volume "$source_office"; then fail "disk guard accepted impossible threshold"; fi

echo "==> Stage 9B reject partial Office backup"
docker run --rm -v "$source_office:/office" postgres:16-alpine sh -c 'ln -s /etc/passwd /office/unsafe-link'
if consistent_backup; then fail "symlink-bearing Office storage was backed up"; fi
[[ "$(docker inspect --format '{{.State.Running}}' "${project}-backend-1")" == true ]] || fail "consistent backup did not restore Backend"
[[ "$(docker inspect --format '{{.State.Running}}' "${project}-worker-1")" == true ]] || fail "consistent backup did not restore Worker"
docker run --rm -v "$source_office:/office" postgres:16-alpine sh -c 'rm /office/unsafe-link'
[[ "$(find "$backup_root/daily" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d " ")" == 1 ]] || fail "partial backup was published"

echo "==> Stage 9B reject checksum corruption and archive traversal"
corrupt="$backup_root/daily/corrupt"
cp -a "$backup_dir" "$corrupt"; printf x >> "$corrupt/office.tar.gz"
if "$BASH" "$script_dir/backup-verify.sh" "$corrupt"; then fail "corrupt backup verified"; fi
rm -rf -- "$corrupt"
traversal="$backup_root/daily/traversal"
cp -a "$backup_dir" "$traversal"
fixture="$(mktemp -d)"; printf x > "$fixture/file.docx"
tar -C "$fixture" --transform='s|^|../|' -czf "$traversal/office.tar.gz" file.docx
rm -rf -- "$fixture"
(cd "$traversal" && sha256sum database.dump office.tar.gz manifest.json > SHA256SUMS)
docker volume create --label "com.docker.compose.project=$project" "$target_office" >/dev/null
create_db "$target_db" "$target_pg"
if "$BASH" "$script_dir/restore.sh" --backup "$traversal" --target isolated --confirm-isolated --project "$project" --db-container "$target_db" --db-name "$db_name" --db-user "$db_user" --office-volume "$target_office"; then fail "traversal archive restore succeeded"; fi
docker rm -f "$target_db" >/dev/null; docker volume rm "$target_pg" "$target_office" >/dev/null
rm -rf -- "$traversal"

echo "==> Stage 9B destroy isolated source and restore fresh targets"
docker rm -f "$source_db" >/dev/null; docker volume rm "$source_pg" "$source_office" >/dev/null
echo "[restore-drill] isolated source destroyed"
create_db "$target_db" "$target_pg"
docker volume create --label "com.docker.compose.project=$project" "$target_office" >/dev/null
echo "[restore-drill] fresh target provisioned"
"$BASH" "$script_dir/restore.sh" --backup "$backup_dir" --target isolated --confirm-isolated --project "$project" --db-container "$target_db" --db-name "$db_name" --db-user "$db_user" --office-volume "$target_office"
echo "[restore-drill] artifacts restored"
[[ "$(docker exec "$target_db" psql -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" -Atc "SELECT max(version) FROM flyway_schema_history WHERE success;")" == 9 ]] || fail "Flyway restore validation failed"
[[ "$(docker exec "$target_db" psql -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" -Atc 'SELECT count(*) FROM submissions;')" == 1 ]] || fail "submission restore validation failed"
[[ "$(docker exec "$target_db" psql -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" -Atc 'SELECT count(*) FROM office_metadata;')" == 1 ]] || fail "Office metadata restore validation failed"
target_hash="$(docker run --rm -v "$target_office:/office:ro" postgres:16-alpine sh -c 'sha256sum "/office/nested/teacher reference.docx" | awk "{print \$1}"')"
[[ "$source_hash" == "$target_hash" ]] || fail "Office binary integrity validation failed"
docker run --rm -v "$target_office:/office:ro" postgres:16-alpine sh -c 'test -f "/office/nested/teacher reference.docx"' || fail "DB-to-Office reference is missing"
docker exec "$target_db" pg_isready -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" >/dev/null || fail "restored database smoke failed"

echo "==> Stage 9B retention dry-run and rotation"
for category_count in 'daily 10' 'weekly 6' 'monthly 5'; do
  set -- $category_count; category="$1"; count="$2"; mkdir -p "$backup_root/$category"
  for ((i=1; i<=count; i++)); do
    copy="$backup_root/$category/2026-01-$(printf '%02d' "$i")T000000Z_fixture-$i"; cp -a "$backup_dir" "$copy"
    sed -i "s/\"createdAt\": \"[^\"]*\"/\"createdAt\": \"2026-01-$(printf '%02d' "$i")T000000Z\"/" "$copy/manifest.json"
    sed -i "s/\"mode\": \"daily\"/\"mode\": \"$category\"/" "$copy/manifest.json"
    (cd "$copy" && sha256sum database.dump office.tar.gz manifest.json > SHA256SUMS)
  done
done
before="$(find "$backup_root" -type d | sort | sha256sum)"
"$BASH" "$script_dir/backup-retention.sh" --backup-root "$backup_root" --dry-run >/dev/null
after="$(find "$backup_root" -type d | sort | sha256sum)"; [[ "$before" == "$after" ]] || fail "retention dry-run changed files"
"$BASH" "$script_dir/backup-retention.sh" --backup-root "$backup_root" --apply >/dev/null
[[ "$(find "$backup_root/daily" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')" == 7 ]] || fail "daily retention count is wrong"
[[ "$(find "$backup_root/weekly" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')" == 4 ]] || fail "weekly retention count is wrong"
[[ "$(find "$backup_root/monthly" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')" == 3 ]] || fail "monthly retention count is wrong"
boundary_before="$(find "$backup_root" -type d | sort | sha256sum)"
"$BASH" "$script_dir/backup-retention.sh" --backup-root "$backup_root" --apply >/dev/null
boundary_after="$(find "$backup_root" -type d | sort | sha256sum)"; [[ "$boundary_before" == "$boundary_after" ]] || fail "exact retention boundary deleted a backup"

echo "Stage 9B backup/restore drill passed"
