# Stage 9B backup and restore

## Authoritative data and recovery target

The authoritative recovery set is PostgreSQL plus the Office persistent
storage volume. PostgreSQL holds users, problems, contests, submissions,
Outbox state, rejudge state, Office metadata, and all derived-data inputs.
The Office volume holds starter files, teacher references, and student Office
submissions. The Backend mounts it at `/app/oj-docs`; Worker and Runner do not
mount or own Office storage.

RabbitMQ messages are not an authoritative backup input. After recovery the
application recreates its topology and the transactional Outbox republishes
durable pending work. A restored Outbox may re-publish pending events, so the
existing idempotency and judge-generation protections remain relevant.

## Backup modes

Set an owner-only host directory outside the repository, for example
`/var/backups/practice-platform`, then run only with an explicit environment,
Compose project, database container/database/user, and Office volume.

```bash
BACKUP_MIN_FREE_BYTES=1073741824 \
  ./scripts/backup.sh --environment staging --mode daily \
  --backup-root /var/backups/practice-platform \
  --project practice-platform-staging \
  --db-container practice-platform-staging-postgres \
  --db-name "$STAGING_POSTGRES_DB" --db-user "$STAGING_POSTGRES_USER" \
  --office-volume practice-platform-staging-docs
```

Daily mode is online: it creates a PostgreSQL 16-compatible custom-format
`pg_dump` and a `tar.gz` Office archive. It has a normal RPO target of 24
hours. It is not a cross-resource atomic snapshot; database metadata and the
Office archive may be slightly skewed.

For weekly, monthly, pre-release, risky-maintenance, or important contest
checkpoints, use `--mode weekly`, `--mode monthly`, or `--mode consistent`
with the exact Compose files. These non-daily modes temporarily stop
Backend and Worker write paths, takes the dump and archive, and uses an EXIT
trap to start them again even when a backup step fails. It provides planned
write quiescence, not a filesystem transaction snapshot.

Each completed backup is atomically published below its category:

```text
<backup-root>/<daily|consistent>/<UTC>_<full-40-char-sha>/
  database.dump
  office.tar.gz
  manifest.json
  SHA256SUMS
  .complete
```

The manifest contains the format version, UTC timestamp, mode, full source
SHA, Flyway version, and artifact names. It never contains runtime credentials.
`SHA256SUMS` covers the dump, archive, and manifest. Partial work remains
unpublished: only a directory with valid checksums and `.complete` is a backup.
Backups use `umask 077`; never put a backup root in Git, a PostgreSQL volume,
or an Office volume.

On Windows Git Bash, use a local Docker Desktop shared-drive directory outside
the repository. The scripts keep `/c/...` paths for host tools and normalize
only Docker bind sources to `C:/...`; spaces and backslash input are supported.
Drive roots and UNC/network-share paths are rejected rather than being mounted
with ambiguous Docker semantics.

The scripts fail before work when `BACKUP_MIN_FREE_BYTES` is not available.
They do not delete existing backups or authoritative data to make space.

## Verification, retention, and restore

```bash
./scripts/backup-verify.sh /var/backups/practice-platform/daily/<backup-id>
./scripts/backup-retention.sh --backup-root /var/backups/practice-platform --dry-run
./scripts/backup-retention.sh --backup-root /var/backups/practice-platform --apply
```

Retention keeps the newest 7 daily, 4 weekly, and 3 monthly verified backups,
ordered by manifest `createdAt`. Dry-run never modifies files. Corrupt or
incomplete directories are reported and are never silently removed. Deletion is
limited to validated direct children of the configured backup root.

Restore defaults to refusal. It accepts only an explicitly confirmed,
test-scoped isolated target (`practice-platform-stage9b-test-*`) with an empty
PostgreSQL database and empty Office volume:

```bash
./scripts/restore.sh --backup /safe/path/<backup-id> \
  --target isolated --confirm-isolated \
  --project practice-platform-stage9b-test-example \
  --db-container practice-platform-stage9b-test-example-db \
  --db-name restore_db --db-user restore_user \
  --office-volume practice-platform-stage9b-test-example-office
```

Before restore, the script requires a supported manifest, `.complete`, all
artifacts, checksums, a full SHA, and safe archive paths. Archives containing
absolute paths, `..`, or links are rejected. It never auto-runs new Flyway
migrations; validate the restored Flyway history (currently V9), database
content, Office file checksums, and DB-to-file references before starting a
recovered application.

## Recovery objectives

- Normal daily RPO target: 24 hours.
- Take an additional consistent backup before releases and important contests.
- Target RTO: 2 hours. This is an operational goal, not a code SLA.
- A disaster restore provisions clean PostgreSQL and Office storage, restores a
  verified backup, validates Flyway and file references, then starts services,
  checks readiness, Outbox/queues, and application smoke.

Staging and Production data must not be restored by these default scripts.
