# Immutable local production release workflow

Production releases follow one traceable local chain:

```text
annotated Git tag
  -> exact source commit
  -> CI
  -> local OCI-labelled images with fixed release tags
  -> recorded local Image IDs
  -> release manifest
  -> preflight
  -> audited application-container replacement
```

Production must never use an application image tagged `latest`, and release
Compose must never contain `build:`. Images are built locally from an exact Git
commit before the maintenance window; deployment consumes only the verified
fixed tags and Image IDs recorded for that release.

## Long-lived Docker environments

- `oj` is Production. Its frontend is `http://localhost:3000`.
- `practice-platform-staging` is Staging. Its frontend is
  `http://localhost:18080`.

Staging is isolated and does not replace Production. Do not stop Production
merely because Staging is running. Temporary rehearsal environments must be
removed after their verification work finishes.

## Files

- `docker-compose.release.yml` is the no-build production topology.
- `.env.production.example` lists the production secret variable names without
  containing any real secret.
- `deploy/releases/v0.4.0-foundation.env.example` records non-secret local image
  and infrastructure identifiers for the current foundation release.
- `docs/release-manifest-template.md` is the sanitised manifest template.
- `scripts/release-preflight.sh` is a fail-closed, read-only gate.
- `scripts/release-status.sh` reports current production state without secrets.

Create the ignored local files before running preflight:

```bash
cp .env.production.example .env.production
cp deploy/releases/v0.4.0-foundation.env.example \
  deploy/releases/v0.4.0-foundation.env
```

Replace every credential placeholder in `.env.production` with the actual
machine-local production value. Replace the backup placeholders in the local
release metadata with existing, verified backup paths. Never copy business
credentials into release metadata or commit either runtime file.

## Building fixed local images

Check out the exact release commit in a clean worktree, then build explicit
release tags and OCI labels. For example:

```bash
release_version=v0.4.0-foundation
release_sha=f1e257d2fc719c2be92fa7cdd8406a98f475a4f1
release_build_time=2026-08-28T07:30:00Z

docker build \
  --label org.opencontainers.image.revision="$release_sha" \
  --label org.opencontainers.image.created="$release_build_time" \
  --label org.opencontainers.image.source=mkbkakwk/Practice-Platform \
  --label org.opencontainers.image.version="$release_version" \
  --tag "oj-backend:$release_version" backend-spring

docker build \
  --label org.opencontainers.image.revision="$release_sha" \
  --label org.opencontainers.image.created="$release_build_time" \
  --label org.opencontainers.image.source=mkbkakwk/Practice-Platform \
  --label org.opencontainers.image.version="$release_version" \
  --tag "oj-worker:$release_version" worker

docker build \
  --label org.opencontainers.image.revision="$release_sha" \
  --label org.opencontainers.image.created="$release_build_time" \
  --label org.opencontainers.image.source=mkbkakwk/Practice-Platform \
  --label org.opencontainers.image.version="$release_version" \
  --tag "oj-runner:$release_version" runner

docker build \
  --label org.opencontainers.image.revision="$release_sha" \
  --label org.opencontainers.image.created="$release_build_time" \
  --label org.opencontainers.image.source=mkbkakwk/Practice-Platform \
  --label org.opencontainers.image.version="$release_version" \
  --tag "oj-frontend:$release_version" frontend
```

Record the complete local Image IDs in the ignored release metadata and release
manifest. A fixed local tag is a human-readable name; the expected Image ID and
OCI revision are the fail-closed identity checks. A registry is not part of the
Production deployment path.

## Read-only gates

Run:

```bash
RELEASE_ENV_FILE=deploy/releases/v0.4.0-foundation.env \
FORMAL_ENV_FILE=.env.production \
  ./scripts/release-preflight.sh

RELEASE_ENV_FILE=deploy/releases/v0.4.0-foundation.env \
  ./scripts/release-status.sh
```

The preflight validates the annotated tag, source ancestry, exact local Image
IDs, OCI labels, fixed local image references, external volumes/network,
required environment variable names, backup existence, Flyway version and
current health. It uses a read-only PostgreSQL session and does not start, stop,
recreate, pull or build anything.

## External production data

The release Compose declares PostgreSQL, RabbitMQ and DOCX volumes plus the
production network as `external: true`. Docker therefore fails instead of
silently creating empty production storage when a name is wrong.

The current RabbitMQ data is held in an audited Docker anonymous volume. Its
exact ID is fixed in the v0.4.0 example so the next release cannot accidentally
attach empty storage. Moving it to a readable named volume requires a separate
backup/recovery rehearsal and is not part of this workflow.

## Deployment boundary

This repository intentionally does not contain an automatic production deploy
script. An explicitly approved maintenance-window procedure may replace
Backend, Runner, Worker and Frontend one at a time after preflight. PostgreSQL,
RabbitMQ, external volumes and the production network must remain untouched.

The existing v0.4.0 images predate the final release tag and therefore retain
OCI version `f1e257d-release`; their full revision and Image IDs remain fixed.
Future local release builds must use the release version as their OCI version.
The Worker is remote-only in release Compose and depends on the trusted Runner;
there is no local Worker sandbox fallback in the release topology. The Runner is
the only application container with Docker-socket access and its readiness check
requires the fixed sandbox images to be available.
