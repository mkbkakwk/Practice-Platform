FROM bash:5.2-alpine3.20

WORKDIR /workspace

COPY docker-compose.release.yml ./
COPY docker-compose.staging.yml ./
COPY .env.production.example ./
COPY .env.staging.example ./
COPY .gitignore ./
COPY README.md ./
COPY deploy/releases/v0.4.0-foundation.env.example deploy/releases/v0.4.0-foundation.env.example
COPY docs/immutable-release-workflow.md docs/immutable-release-workflow.md
COPY docs/release-manifest-template.md docs/release-manifest-template.md
COPY scripts/release-common.sh scripts/release-common.sh
COPY scripts/release-metadata.sh scripts/release-metadata.sh
COPY scripts/release-preflight.sh scripts/release-preflight.sh
COPY scripts/release-status.sh scripts/release-status.sh
COPY scripts/staging-common.sh scripts/staging-common.sh
COPY scripts/test-release-metadata.sh scripts/test-release-metadata.sh
COPY scripts/test-release-config.sh scripts/test-release-config.sh
COPY frontend/src/components/Navbar.tsx frontend/src/components/Navbar.tsx
COPY .github/workflows .github/workflows

CMD ["bash", "scripts/test-release-config.sh"]
