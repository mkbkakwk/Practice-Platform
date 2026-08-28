# Stage 9A release topology and health semantics

Stage 9A keeps operational truth close to the running services without adding a
second scoring, queue, or monitoring system. It does not deploy, back up, or
restore data.

## Release topology

The immutable release Compose topology contains Frontend, Backend, PostgreSQL,
RabbitMQ, Runner, and Worker. PostgreSQL, RabbitMQ, the DOCX storage volume,
and the production network remain external persistent resources.

The Worker runs only in `remote` judge mode in release Compose. It requires the
trusted Runner URL and Runner token at interpolation time, so a missing Runner
configuration cannot silently fall back to local execution. The Runner is the
only application service with Docker-socket access. It runs untrusted student
code only in the existing disposable sandbox containers; those containers never
receive the control-plane socket.

## Endpoint contract

| Service | Liveness | Readiness | Meaning |
| --- | --- | --- | --- |
| Backend | `GET /api/health` | `GET /api/readiness` | Liveness is public and returns only `status`. Readiness requires PostgreSQL and initialized Flyway. RabbitMQ is deliberately not a hard backend-readiness dependency because the transactional Outbox safely persists work during a broker outage. |
| Worker | `GET /api/health` | `GET /api/readiness` | Readiness requires PostgreSQL, an active RabbitMQ listener, and a ready Runner. |
| Runner | `GET /api/liveness` | `GET /api/readiness` | Readiness reuses the established `sandboxAvailable` signal, including Docker Engine and required sandbox-image capability. |

Liveness must not turn an ordinary dependency outage into a restart loop.
Readiness returns `503` while a service cannot safely accept its corresponding
work, but the service process remains alive and can recover when its dependency
recovers. Each public or container health response contains only `status` and
never reveals hostnames, queue depth, storage paths, credentials, or tokens.

Dependency probes run outside HTTP request threads with a bounded 750 ms
budget. A stalled dependency therefore produces a fast `503` instead of
occupying request threads until a pool or network default timeout expires.

Worker readiness requires both an active Rabbit listener lifecycle and a
bounded, non-mutating AMQP connection/channel probe. This detects a broker
outage even when Spring's listener registry is still marked running; it does
not publish messages or alter queues, exchanges, or bindings.

## Version evidence

The Backend receives `APP_GIT_SHA`, `APP_VERSION`, and `APP_BUILD_TIME` from
immutable release metadata. `APP_GIT_SHA` is always the full 40-character
source revision; the shorter image tag used by Staging remains separate. The
build timestamp is a UTC ISO-8601 artifact value, not container startup time.
`GET /api/admin/version` is Admin-only and reports those three values plus the
current Flyway version. Public health does not report version or schema data.

The frontend and backend release images are built from the same recorded
release commit and carry the same OCI revision label. The Staging-only frontend
badge remains deliberately gated to Staging builds.

## Release preflight

`scripts/release-preflight.sh` remains read-only. It validates fixed local
image IDs and OCI labels, required PostgreSQL/RabbitMQ/JWT/Runner variables,
the Runner image and container, external persistent resources, the resolved
Compose file, Flyway version, and liveness. It prints only presence and
validation outcomes, never secret values.

Backups, retention, dashboards, Prometheus metrics, structured logging, and
recovery drills are intentionally outside Stage 9A.
