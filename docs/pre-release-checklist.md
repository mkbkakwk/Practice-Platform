# Release Checklist by Phase

School operators: follow [SCHOOL_DEPLOYMENT.md](SCHOOL_DEPLOYMENT.md) and [OPERATIONS.md](OPERATIONS.md). This checklist records gates; it does not authorize a deployment. Test and Staging smoke may create isolated data; Production pre-commit acceptance must not.

## Before maintenance

- [ ] Exact accepted tag/source and four local application Image IDs/OCI labels are recorded; no moving branch or rebuild during deployment.
- [ ] Current Production identity, Flyway, six-service health, Office references and storage mappings are verified.
- [ ] Work is drained: queues, unacknowledged messages, nonterminal Outbox and unexpected legacy work are understood and quiescent.
- [ ] OPS credentials exist and authenticate; full ops-check is scheduled after commit, not during holdback.
- [ ] External env-forwarding backup wrapper, isolated success/failure evidence, destination capacity and secure evidence capture are ready.
- [ ] Restore drill and exact pre-commit recovery procedure are approved, including schema compatibility, DB+Office pairing and zero-message conditions.
- [ ] Known-good `ROLLBACK_SHA` is explicitly identified and verified as an accessible full commit SHA; the rollback target is compatible with the corresponding database and Office recovery state.
- [ ] The independent business-access barrier and operator-only inspection path have been rehearsed.

## Maintenance and fresh T1

- [ ] Access is closed; Frontend and Worker are stopped; no in-flight work remains.
- [ ] A new consistent backup from this boundary has exit 0, manifest, checksums, archive integrity, completion marker and correct tool/runtime provenance.
- [ ] Dump/restore and DB-to-Office evidence meet the approved recovery contract; the T1 is not an old backup from before resumed business activity.
- [ ] Worker is re-held after backup cleanup; Frontend remains held; queues/Outbox remain quiescent.

## Pre-commit holdback

- [ ] Only new Backend and Runner have started; existing DB/RabbitMQ storage is preserved.
- [ ] Expected Flyway only, exact identity, Backend/Runner readiness, sandbox, Office and zero-message/nonterminal-Outbox gates pass.
- [ ] Worker and Frontend are actually not running; normal business access remains closed; no mutating smoke has run.
- [ ] Full ops-check and Frontend-dependent authenticated workflows have not been misapplied as holdback gates.

## Commit and observation

- [ ] Start only Worker; verify identity, readiness, remote Runner and zero-message/Outbox recheck.
- [ ] Start only Frontend; verify loopback port publication, routing and six-service health while the independent access barrier remains closed.
- [ ] Record release commit UTC; only then reopen business access and record UTC.
- [ ] Run full-topology Admin/Auth, Contest/Analytics and Office read checks, full ops-check and operational observation. Do not invent Production fixtures to satisfy a read-only check.
- [ ] Record any missing-fixture deferral honestly; actual failures require incident-specific assessment, not automatic old-version restore.
- [ ] Retain T1, previous images and sanitized evidence; record maintenance end UTC after acceptance. Never print secrets or prune recovery evidence as part of closeout.
