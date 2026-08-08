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
| CI Run | `<GitHub Actions run URL/ID>` |
| Release PR | `<PR URL/number>` |

## Immutable images

| Component | Image | Registry Digest | OCI Revision | OCI Version |
| --- | --- | --- | --- | --- |
| Backend | `<registry/name:tag>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |
| Worker | `<registry/name:tag>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |
| Frontend | `<registry/name:tag>` | `<sha256:...>` | `<full SHA>` | `<release tag>` |

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
| PostgreSQL Backup | `<path/reference and SHA-256>` |
| RabbitMQ Definitions | `<path/reference and SHA-256>` |
| DOCX Backup | `<path/reference and SHA-256>` |
| Rollback Backend Image | `<immutable image ID/digest>` |
| Rollback Worker Image | `<immutable image ID/digest>` |
| Rollback Frontend Image | `<immutable image ID/digest>` |

## Verification

- [ ] Tag resolves to Release Git SHA.
- [ ] OCI revision and version labels match this manifest.
- [ ] Image tags resolve to the recorded registry digests.
- [ ] Flyway reports the expected version with no unexpected migration.
- [ ] All external volumes and the production network exist.
- [ ] Backend and frontend health checks pass.
- [ ] Rollback images and backups are accessible.
- [ ] Production counts and health are recorded before and after deployment.
