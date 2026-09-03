# Pre-release Checklist

- [ ] `DEPLOY_SHA` is an exact 40-character commit and matches green CI.
- [ ] Git worktree is clean; main is unchanged.
- [ ] Flyway version and rollback compatibility are known.
- [ ] A fresh consistent external backup has manifest, checksums, and `.complete`.
- [ ] Current isolated restore evidence, measured RPO, and measured RTO are recorded.
- [ ] Disk policy and latest backup age pass `ops-check.sh`.
- [ ] Main/Retry/DLQ and Outbox are healthy; restart state is understood.
- [ ] Staging is healthy on the exact intended SHA with matching OCI revisions.
- [ ] Admin version, build time, readiness, sandbox, JSON logging, and redaction pass.
- [ ] Algorithm Judge AC, Contest, Office, and Analytics smoke pass.
- [ ] Known-good `ROLLBACK_SHA` is accessible and the rollback runbook was reviewed.
- [ ] No Production secret, token, credential, or backup artifact is printed or committed.
- [ ] Release sign-off records UTC time, SHA, CI run, backup ID, operator, and rollback SHA.
