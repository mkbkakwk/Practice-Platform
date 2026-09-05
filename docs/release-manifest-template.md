# Practice Platform Release Manifest

This template contains identifiers only. Never record passwords, JWT secrets,
tokens, user data, or raw environment files here.

## Release identity

| Field | Value |
| --- | --- |
| Release Version | `<version>` |
| Release Git SHA | `<full commit SHA used to build>` |
| Main Merge Commit | `<full main merge commit SHA>` |
| Release Tag | `<annotated tag>` |
| Previous Production Git SHA | `<full previous runtime SHA>` |
| Backup Tool Git SHA | `<full backup-tool checkout SHA>` |
| T1 Production Runtime Git SHA | `<full runtime SHA recorded in the T1 backup>` |
| CI Run | `<GitHub Actions run URL/ID>` |
| Release PR | `<PR URL/number>` |

## Immutable local images

| Component | Local Image Reference | Local Image ID | OCI Revision | OCI Version |
| --- | --- | --- | --- | --- |
| Backend | `oj-backend:<release>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |
| Worker | `oj-worker:<release>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |
| Runner | `oj-runner:<release>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |
| Frontend | `oj-frontend:<release>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |

## Runtime and persistence

| Field | Value |
| --- | --- |
| Flyway Version | `<version>` |
| Deployment Timestamp | `<ISO-8601 timestamp>` |
| Database Volume | `<external volume>` |
| RabbitMQ Volume | `<external volume>` |
| DOCX Volume | `<external volume>` |
| Network | `<external network>` |
| Frontend Port | `<host binding>` |
| Health Endpoint | `<URL>` |

## Recovery

| Field | Value |
| --- | --- |
| Backup ID | `<backup set identifier>` |
| T1 Backup Tool Git SHA | `<manifest backupToolGitSha>` |
| T1 Production Runtime Git SHA | `<manifest productionRuntimeGitSha>` |
| PostgreSQL Backup | `<path/reference and SHA-256>` |
| RabbitMQ Definitions | `<path/reference and SHA-256>` |
| DOCX Backup | `<path/reference and SHA-256>` |
| Rollback Backend Image | `<immutable image ID/digest>` |
| Rollback Worker Image | `<immutable image ID/digest>` |
| Rollback Runner Image | `<immutable image ID/digest or NOT PRESENT in previous topology>` |
| Rollback Frontend Image | `<immutable image ID/digest>` |

## Verification

- [ ] Tag resolves to Release Git SHA.
- [ ] OCI revision and version labels match this manifest.
- [ ] Local image tags resolve to the recorded Image IDs.
- [ ] Flyway reports the expected version with no unexpected migration.
- [ ] All external volumes and the production network exist.
- [ ] PostgreSQL, RabbitMQ, Backend, Worker, Runner and Frontend health checks pass at the full-topology gate.
- [ ] Worker uses remote Runner; only Runner mounts the Docker socket.
- [ ] OPS credential readiness was proven before maintenance; full ops-check runs post-commit.
- [ ] Rollback images and backups are accessible.
- [ ] Production counts and health are recorded before and after deployment.
