FROM bash:5.2-alpine3.20

WORKDIR /workspace

COPY docker-compose.release.yml ./
COPY .env.production.example ./
COPY .gitignore ./
COPY README.md ./
COPY deploy/releases/v0.4.0-foundation.env.example deploy/releases/v0.4.0-foundation.env.example
COPY docs/immutable-release-workflow.md docs/immutable-release-workflow.md
COPY docs/release-manifest-template.md docs/release-manifest-template.md
COPY scripts/release-common.sh scripts/release-common.sh
COPY scripts/release-preflight.sh scripts/release-preflight.sh
COPY scripts/release-status.sh scripts/release-status.sh
COPY scripts/test-release-config.sh scripts/test-release-config.sh
COPY scripts/runner-linux-preflight.sh scripts/runner-linux-preflight.sh
COPY scripts/test-runner-linux-preflight.sh scripts/test-runner-linux-preflight.sh
COPY scripts/test-runner-linux.sh scripts/test-runner-linux.sh
COPY scripts/runner-linux-acceptance-inner.sh scripts/runner-linux-acceptance-inner.sh
COPY scripts/test-runner-linux-acceptance.sh scripts/test-runner-linux-acceptance.sh
COPY scripts/test-fixtures/runner-preflight-command-mock.sh scripts/test-fixtures/runner-preflight-command-mock.sh
COPY runner/pom.xml runner/pom.xml
COPY runner/security/nsjail-seccomp.policy runner/security/nsjail-seccomp.policy
COPY frontend/src/components/Navbar.tsx frontend/src/components/Navbar.tsx
COPY .github/workflows .github/workflows

CMD ["bash", "scripts/test-release-config.sh"]
