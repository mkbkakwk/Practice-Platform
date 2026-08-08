FROM bash:5.2-alpine3.20

WORKDIR /workspace

COPY docker-compose.release.yml ./
COPY deploy/releases/v0.4.0-foundation.env.example deploy/releases/v0.4.0-foundation.env.example
COPY docs/release-manifest-template.md docs/release-manifest-template.md
COPY scripts/release-common.sh scripts/release-common.sh
COPY scripts/release-preflight.sh scripts/release-preflight.sh
COPY scripts/release-status.sh scripts/release-status.sh
COPY scripts/test-release-config.sh scripts/test-release-config.sh
COPY frontend/src/components/Navbar.tsx frontend/src/components/Navbar.tsx
COPY .github/workflows/publish-release-images.yml .github/workflows/publish-release-images.yml
COPY .github/workflows/ci.yml .github/workflows/ci.yml

CMD ["bash", "scripts/test-release-config.sh"]
