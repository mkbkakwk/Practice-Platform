# Immutable production release workflow

Production releases follow one traceable chain:

```text
annotated Git tag
  -> exact source commit
  -> CI
  -> OCI-labelled image
  -> registry digest
  -> release manifest
  -> preflight
  -> audited application-container replacement
```

Production must never use an application image tagged `latest`, and release
Compose must never contain `build:`. Image construction happens before the
maintenance window; deployment consumes only verified images.

## Files

- `docker-compose.release.yml` is the no-build production topology.
- `deploy/releases/v0.4.0-foundation.env.example` records non-secret image and
  infrastructure identifiers for the current foundation release.
- `docs/release-manifest-template.md` is the sanitised manifest template.
- `scripts/release-preflight.sh` is a fail-closed, read-only gate.
- `scripts/release-status.sh` reports current production state without secrets.
- `.github/workflows/publish-release-images.yml` is a manual GHCR publisher.

Copy the release example to the ignored path before running preflight:

```bash
cp deploy/releases/v0.4.0-foundation.env.example \
  deploy/releases/v0.4.0-foundation.env
```

Replace only the five backup placeholders with existing, verified backup file
paths. Business credentials remain in the separately protected `.env`; never
copy them into release metadata.

Run the read-only gates:

```bash
RELEASE_ENV_FILE=deploy/releases/v0.4.0-foundation.env \
  ./scripts/release-preflight.sh

RELEASE_ENV_FILE=deploy/releases/v0.4.0-foundation.env \
  ./scripts/release-status.sh
```

The preflight validates the annotated tag, source ancestry, exact local Image
IDs, OCI labels, image digests, external volumes/network, required environment
variable names, backup existence, Flyway version and current health. It uses a
read-only PostgreSQL session and does not start, stop, recreate, pull or build
anything.

## External production data

The release Compose declares PostgreSQL, RabbitMQ and DOCX volumes plus the
production network as `external: true`. Docker therefore fails instead of
silently creating empty production storage when a name is wrong.

The current RabbitMQ data is held in an audited Docker anonymous volume. Its
exact ID is fixed in the v0.4.0 example so the next release cannot accidentally
attach empty storage. Moving it to a readable named volume requires a separate
backup/recovery rehearsal and is not part of this release-hardening change.

## GHCR publication preparation

Target names:

```text
ghcr.io/mkbkakwk/practice-platform-backend:<git-sha-or-release-tag>
ghcr.io/mkbkakwk/practice-platform-worker:<git-sha-or-release-tag>
ghcr.io/mkbkakwk/practice-platform-frontend:<git-sha-or-release-tag>
```

The manual workflow uses the scoped `GITHUB_TOKEN`; no PAT belongs in Git,
Dockerfiles or documentation. Before its first invocation:

1. confirm the three package names do not conflict with an existing package;
2. decide and review private/public package visibility;
3. configure the `ghcr-release` GitHub Environment with required reviewers;
4. verify Actions may write packages;
5. select `confirmed-private-or-approved-public` in the manual dispatch form.

The workflow refuses to overwrite an existing Git-SHA or release-version tag.
Each image receives:

```text
org.opencontainers.image.revision=<full source SHA>
org.opencontainers.image.source=https://github.com/mkbkakwk/Practice-Platform
org.opencontainers.image.version=<annotated release tag>
```

It records the resulting registry digest as an artifact for the release
manifest. Production Compose should be updated to the resulting
`ghcr.io/...@sha256:...` references only in a later, explicitly approved
release.

## Deployment boundary

This repository intentionally does not contain an automatic production deploy
script. A later approved maintenance-window procedure may replace Backend,
Worker and Frontend one at a time after preflight. PostgreSQL, RabbitMQ,
external volumes and the production network must remain untouched.

The existing v0.4.0 images predate the final release tag and therefore retain
OCI version `f1e257d-release`; their full revision and Image IDs remain fixed.
All newly published registry images must use the annotated release tag as the
OCI version.
