# Backup and Restore Runbook

> 学校操作员请先读 [OPERATIONS.md](OPERATIONS.md)。下面的 Staging 示例保留为技术演练；正式备份须用已验证的外部 wrapper 将 formal/release dotenv 显式转发给每次内部 Compose 调用。非 daily 备份退出时会尝试启动 Backend/Worker，发布窗口必须随后重新 hold Worker。不要用隔离 restore 命令指向 Production。

This operator runbook complements `backup-restore.md`. Authoritative recovery data is PostgreSQL plus Office persistent files. RabbitMQ message storage is not an authoritative backup.

## Backup

Use an external host directory outside Git, PostgreSQL volumes, and Office volumes. Daily backups are online and may have slight DB/Office skew; releases and recovery drills require a consistent logical pair with planned Backend/Worker write quiescence.

```bash
./scripts/backup.sh --environment staging --mode consistent \
  --backup-root <external-backup-root> \
  --project practice-platform-staging \
  --db-container practice-platform-staging-postgres \
  --db-name "$STAGING_POSTGRES_DB" --db-user "$STAGING_POSTGRES_USER" \
  --office-volume practice-platform-staging-docs \
  --compose-file docker-compose.yml --compose-file docker-compose.staging.yml
```

The script verifies free space before publication, writes a custom PostgreSQL dump and Office archive, records separate backup-tool Git SHA, backed-up runtime Git SHA, Flyway, and UTC metadata, checks SHA-256 and archive safety, atomically publishes `.complete`, and restarts Backend/Worker through an EXIT trap. New artifacts use `umask 077`: Linux meaningful modes are directory `700` and files `600`; Windows keeps compatible ACL semantics and must never be world-writable.

Formal Production backups must additionally supply `--production-runtime-git-sha <full-sha>`. The release T1 preflight verifies this value is the observed previous Production runtime; it never substitutes the backup tool checkout SHA.

Run `./scripts/backup-verify.sh <backup-dir>` before considering a backup usable. Retention is `7 daily / 4 weekly / 3 monthly`; first use only `backup-retention.sh --dry-run`. Corrupt or incomplete directories are retained for investigation rather than deleted automatically.

## Isolated restore

Never restore to Staging or Production. Provision fresh PostgreSQL, fresh Office volume, isolated network, and a project named `practice-platform-stage9d-drill-*`, then run:

```bash
./scripts/restore.sh --backup <verified-backup-dir> --target isolated --confirm-isolated \
  --project practice-platform-stage9d-drill-example --db-container <fresh-db-container> \
  --db-name <fresh-db-name> --db-user <fresh-db-user> --office-volume <fresh-office-volume>
```

The guard rejects live/non-isolated target names, non-empty targets, incomplete/checksum-invalid backups, archive traversal, and links. Validate Flyway V9 and failed migrations `0`; backup-time DB counts; Office file counts and representative SHA-256; and DB-to-Office references. Start an isolated application only if every dependency, including RabbitMQ, is isolated. Record backup age and restore duration as RPO/RTO evidence, then remove only the drill's resources.
