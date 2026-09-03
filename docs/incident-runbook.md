# Incident and Troubleshooting Runbook

Start with read-only evidence: `ops-check.sh`, `staging-status.sh`, bounded health/readiness endpoints, and structured logs. Do not use `docker system prune`, `docker volume prune`, volume deletion, queue purge, Outbox deletion, database drop, or `rm -rf` as routine recovery.

| Symptom | Check first | Healthy state / safe recovery | Stop and escalate |
| --- | --- | --- | --- |
| Backend unhealthy | health, readiness, PostgreSQL/Flyway, version SHA | health/readiness 200; correct SHA; known-good application redeploy only if compatible | migration or data compatibility uncertain |
| Worker unhealthy | DB, Rabbit listener/connectivity, Runner readiness | Worker readiness 200; Runner sandbox available | retry/DLQ grows or Worker repeatedly restarts |
| Runner unhealthy | Runner readiness and images | readiness 200 and sandboxAvailable true | Docker/socket/image policy cannot be restored safely |
| PostgreSQL unhealthy | container health, bounded probe, disk | DB healthy; Flyway known | corruption or restore to a live target is contemplated |
| RabbitMQ unhealthy | container health, Worker readiness, queues | Backend may remain ready; Worker recovers | DLQ/nonzero retry backlog persists |
| Judge stuck / Outbox backlog | submission ID, event ID, Outbox, queues | event publishes and Worker commits verdict | stale backlog or repeated infrastructure failure |
| Office file missing | DB record-to-file reference, volume health | file exists and checksum matches backup | restore target would be live |
| Disk low / backup stale | ops-check disk and backup age | free space and valid current backup | retention would delete evidence |
| Version mismatch / restart loop | Admin version, OCI labels, restart counts | exact SHA and zero unexpected restarts | migration compatibility or data loss suspected |
| JSON logging regression | structured records and release logging gate | safe fields retained; endpoints/secrets absent | any secret/connection metadata appears |

Record UTC time, exact SHA, component, safe symptom, correlation/submission/event IDs where relevant, read-only checks, recovery action, and post-recovery verification. Production requires a separate approved change window.
