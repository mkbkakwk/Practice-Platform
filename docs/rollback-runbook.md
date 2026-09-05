# Rollback Runbook

> 当前校园运行／恢复边界见 [OPERATIONS.md](OPERATIONS.md)。本文的同 schema Staging 回退演练不授权 Production V9→V4。Production 提交前跨 schema 回退需要该次 fresh T1 配对 DB+Office、零消息条件及已批准的清洁目标恢复；不能用通用 `pg_restore --clean` 替代。`restore.sh` 仅允许隔离空目标。提交点后不得自动 V4 回退，必须 incident-specific 评估。

Application rollback replaces application containers with a previously verified application SHA. It is not a database rollback. Never assume an old application is compatible with a newer schema: before a future migration release, classify the migration as backward-compatible or rollback-blocking. Stage 9D remains Flyway V9, so its rehearsal has no schema rollback.

## When rollback is allowed

Rollback when the new application SHA fails health/readiness, release metadata, critical smoke, security/logging, or sustained queue/Outbox gates and the previous known-good SHA is compatible with the database. Stop and escalate when data compatibility is uncertain, a destructive data action seems necessary, or the rollback SHA cannot be independently verified.

## Staging rollback sequence

```bash
ROLLBACK_SHA=<previous-known-good-40-char-sha>
./scripts/staging-deploy-sha.sh --sha "$ROLLBACK_SHA"
./scripts/staging-smoke.sh
```

This preserves Staging PostgreSQL, RabbitMQ, Office volumes, and external backups. It does not use `down -v`, volume deletion, queue purge, or schema rollback. Verify Admin version, OCI revisions, health/readiness, `sandboxAvailable`, queues/Outbox, and one Algorithm AC. Compare safe counts for users, problems, contests, submissions, Outbox, and Office records/files with the pre-rollback snapshot.

RabbitMQ messages are non-authoritative backup state; PostgreSQL plus Office files are authoritative. Durable asynchronous recovery relies on the transactional Outbox, not a RabbitMQ-volume restore.

## Forward recovery

After rollback evidence is accepted, redeploy the intended exact SHA with `staging-deploy-sha.sh --sha "$DEPLOY_SHA"`. Re-run version, health, data-preservation, Judge, queue, and Outbox gates. Do not declare recovery complete until the forward SHA is again deployed.
