# Release Runbook

This runbook is for a controlled release rehearsal or a separately approved Production maintenance window. It does not authorize Production changes by itself. Every command is bound to `DEPLOY_SHA`, an exact 40-character Git commit; never substitute `HEAD`, `main`, `latest`, or a branch name.

## Stop conditions

Stop before deployment when Git is dirty, the requested SHA is not the CI-tested revision, Flyway is not the expected version, a fresh verified backup is absent, disk space is below the configured threshold, queues/DLQ or Outbox are unhealthy, or the known rollback SHA is unknown. Do not try to repair these conditions with pruning, queue purges, volume deletion, or schema rollback.

## Pre-release gate

```bash
DEPLOY_SHA=<40-char-sha>
ROLLBACK_SHA=<previous-known-good-40-char-sha>
git status --short
git rev-parse "$DEPLOY_SHA"
./scripts/ops-check.sh --environment staging --expected-sha "$ROLLBACK_SHA" --backup-root <external-backup-root>
```

Confirm green Docker CI for `DEPLOY_SHA`, Flyway V9, a current consistent backup with valid manifest/checksums/`.complete`, and an isolated restore drill. Record the OCI revision, backup ID, operator, UTC start time, and rollback SHA.

## Staging deployment sequence

`staging-deploy-sha.sh` creates a detached temporary worktree and invokes the existing Staging topology from that exact source. It only targets `practice-platform-staging` and preserves PostgreSQL, RabbitMQ, Office, and external backup data.

```bash
./scripts/staging-deploy-sha.sh --sha "$DEPLOY_SHA"
./scripts/staging-smoke.sh
```

Use a token-safe one-shot client to confirm `/api/admin/version` returns the same 40-character SHA, valid immutable UTC build time, and Flyway 9. Confirm all health/readiness states, `sandboxAvailable`, Backend/Worker/Frontend OCI revisions, queues, and Outbox. Run one Algorithm AC plus lightweight Contest, Analytics, and Office smoke.

## Post-release observation and sign-off

Run `ops-check.sh` against the deployed SHA. Record queue depths, DLQ, Outbox nonterminal count, restart state, backup age, free disk, Judge result, and warnings. Sign off only after the exact deployed SHA, health, and smoke evidence all match. Production additionally requires the approved maintenance-window procedure in `immutable-release-workflow.md`.
