#!/usr/bin/env sh

set -eu

compose_file="docker-compose.release.yml"
release_example="deploy/releases/v0.4.0-foundation.env.example"
manifest_template="docs/release-manifest-template.md"
publish_workflow=".github/workflows/publish-release-images.yml"
ci_workflow=".github/workflows/ci.yml"

fail() {
  echo "RELEASE CONFIG TEST FAILED: $*" >&2
  exit 1
}

for file in "$compose_file" "$release_example" "$manifest_template" "$publish_workflow" "$ci_workflow"; do
  [ -f "$file" ] || fail "missing $file"
done

for script in scripts/release-common.sh scripts/release-preflight.sh scripts/release-status.sh; do
  bash -n "$script" || fail "invalid Bash syntax in $script"
done

if grep -Eq '^[[:space:]]+build:' "$compose_file"; then
  fail "release Compose must not contain build directives"
fi
if grep -Eqi '(^|[^[:alnum:]_])latest([^[:alnum:]_]|$)' "$compose_file" "$release_example"; then
  fail "release configuration must not use latest"
fi
if grep -Eqi 'staging' "$compose_file"; then
  fail "release Compose must not reference staging"
fi

for image_key in POSTGRES_IMAGE RABBITMQ_IMAGE BACKEND_IMAGE WORKER_IMAGE FRONTEND_IMAGE; do
  value="$(awk -F= -v key="$image_key" '$1 == key {sub(/^[^=]*=/, ""); print}' "$release_example")"
  [ -n "$value" ] || fail "$image_key is empty"
  printf '%s' "$value" | grep -Eq '@sha256:[0-9a-f]{64}$' \
    || fail "$image_key is not pinned by digest"
done

for required_ref in \
  'image: ${BACKEND_IMAGE:?BACKEND_IMAGE is required}' \
  'image: ${WORKER_IMAGE:?WORKER_IMAGE is required}' \
  'image: ${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}'; do
  grep -Fq "$required_ref" "$compose_file" || fail "missing required image reference: $required_ref"
done

[ "$(grep -c 'external: true' "$compose_file")" -eq 4 ] \
  || fail "all three data volumes and the production network must be external"
for resource in FORMAL_POSTGRES_VOLUME FORMAL_RABBITMQ_VOLUME FORMAL_DOCS_VOLUME FORMAL_NETWORK; do
  grep -Fq "\${${resource}:?" "$compose_file" || fail "missing external resource variable $resource"
done

if awk '
  /POSTGRES_PASSWORD:|RABBITMQ_DEFAULT_PASS:|RABBITMQ_PASSWORD:|JWT_SECRET:/ {
    if ($0 !~ /\$\{[A-Z0-9_]+/) exit 1
  }
' "$compose_file"; then :; else
  fail "release Compose contains a hard-coded secret"
fi

grep -Fq 'PROMOTE_FIRST_ADMIN: "false"' "$compose_file" \
  || fail "production first-admin promotion must be disabled"
grep -Fq 'config --format json' scripts/release-preflight.sh \
  || fail "release preflight must inspect the resolved Compose configuration"
grep -Fq 'resolved Release Compose must force PROMOTE_FIRST_ADMIN=false' scripts/release-preflight.sh \
  || fail "release preflight must fail when first-admin promotion is enabled"
if grep -Eq 'VITE_DEPLOY_ENV|VITE_BUILD_SHA' "$compose_file"; then
  fail "production release Compose must not inject the staging badge"
fi
grep -Fq 'deployEnvironment === "staging"' frontend/src/components/Navbar.tsx \
  || fail "staging badge is not explicitly gated"

for field in 'Release Version' 'Release Git SHA' 'Main Merge Commit' \
  'Backend Image' 'Worker Image' 'Frontend Image' 'Flyway Version' \
  'Database Volume' 'RabbitMQ Volume' 'DOCX Volume' 'Backup ID' 'Release PR'; do
  grep -Fq "$field" "$manifest_template" || fail "manifest template is missing $field"
done

grep -Fq 'workflow_dispatch:' "$publish_workflow" || fail "registry workflow must be manual"
grep -Fq 'packages: write' "$publish_workflow" || fail "registry workflow lacks packages write"
grep -Fq 'confirm_package_visibility' "$publish_workflow" || fail "registry workflow lacks visibility confirmation"
grep -Fq 'needs: release-gate' "$publish_workflow" || fail "registry publication must depend on the approval gate"
grep -Fq 'exit 1' "$publish_workflow" || fail "unconfirmed registry publication must fail"
grep -Fq 'secrets.GITHUB_TOKEN' "$publish_workflow" || fail "registry workflow must use GitHub OIDC-scoped token"
grep -Fq 'uses: docker/setup-buildx-action@v3' "$publish_workflow" \
  || fail "registry workflow must initialize Docker Buildx"
grep -Fq 'driver: docker-container' "$publish_workflow" \
  || fail "registry workflow must use the docker-container Buildx driver"
setup_buildx_line="$(grep -nF 'uses: docker/setup-buildx-action@v3' "$publish_workflow" | head -n 1 | cut -d: -f1)"
build_push_line="$(grep -nF 'uses: docker/build-push-action@v6' "$publish_workflow" | head -n 1 | cut -d: -f1)"
[ "$setup_buildx_line" -lt "$build_push_line" ] \
  || fail "Buildx must be initialized before the image build"
grep -Fq 'provenance: mode=max' "$publish_workflow" \
  || fail "registry workflow must keep maximum provenance attestation"
grep -Fq 'sbom: true' "$publish_workflow" \
  || fail "registry workflow must keep SBOM attestation"
if grep -Eqi 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}' "$publish_workflow"; then
  fail "registry workflow contains a plaintext token"
fi

grep -Fq 'Reject tracked runtime environment files' "$ci_workflow" \
  || fail "CI must reject tracked runtime environment files"
grep -Fq "git ls-files -- .env .env.staging 'deploy/releases/*.env'" "$ci_workflow" \
  || fail "CI must inspect the Git index for runtime environment files"

echo "Release configuration checks passed (immutable images, external data, no secrets, no staging)."
