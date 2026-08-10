#!/usr/bin/env sh

set -eu

compose_file="docker-compose.release.yml"
release_example="deploy/releases/v0.4.0-foundation.env.example"
production_env_example=".env.production.example"
manifest_template="docs/release-manifest-template.md"
release_workflow_doc="docs/immutable-release-workflow.md"
publish_workflow=".github/workflows/publish-release-images.yml"
ci_workflow=".github/workflows/ci.yml"

fail() {
  echo "RELEASE CONFIG TEST FAILED: $*" >&2
  exit 1
}

for file in "$compose_file" "$release_example" "$production_env_example" \
  "$manifest_template" "$release_workflow_doc" "$ci_workflow"; do
  [ -f "$file" ] || fail "missing $file"
done
[ ! -e "$publish_workflow" ] || fail "remote registry publishing workflow must be removed"

for script in scripts/release-common.sh scripts/release-preflight.sh scripts/release-status.sh; do
  bash -n "$script" || fail "invalid Bash syntax in $script"
done
bash -n scripts/runner-linux-preflight.sh \
  || fail "invalid Bash syntax in scripts/runner-linux-preflight.sh"
bash -n scripts/test-runner-linux-preflight.sh \
  || fail "invalid Bash syntax in scripts/test-runner-linux-preflight.sh"
bash scripts/test-runner-linux-preflight.sh \
  || fail "Runner Linux preflight compatibility checks failed"

if grep -Eq '^[[:space:]]+build:' "$compose_file"; then
  fail "release Compose must not contain build directives"
fi
if grep -Eqi '(^|[^[:alnum:]_])latest([^[:alnum:]_]|$)' "$compose_file" "$release_example"; then
  fail "release configuration must not use latest"
fi
if grep -Eqi 'staging' "$compose_file"; then
  fail "release Compose must not reference staging"
fi
if grep -Eqi 'ghcr\.io|GitHub Container Registry|GHCR' \
  "$compose_file" "$release_example" "$manifest_template" "$release_workflow_doc" README.md; then
  fail "production release documentation and configuration must not require GHCR"
fi

for image_key in POSTGRES_IMAGE RABBITMQ_IMAGE; do
  value="$(awk -F= -v key="$image_key" '$1 == key {sub(/^[^=]*=/, ""); print}' "$release_example")"
  [ -n "$value" ] || fail "$image_key is empty"
  printf '%s' "$value" | grep -Eq '@sha256:[0-9a-f]{64}$' \
    || fail "$image_key is not pinned by digest"
done

release_version="$(awk -F= '$1 == "RELEASE_VERSION" {sub(/^[^=]*=/, ""); print}' "$release_example")"
[ -n "$release_version" ] || fail "RELEASE_VERSION is empty"
for image_spec in \
  "BACKEND_IMAGE:oj-backend" \
  "WORKER_IMAGE:oj-worker" \
  "FRONTEND_IMAGE:oj-frontend"; do
  image_key="${image_spec%%:*}"
  local_repo="${image_spec#*:}"
  value="$(awk -F= -v key="$image_key" '$1 == key {sub(/^[^=]*=/, ""); print}' "$release_example")"
  [ "$value" = "$local_repo:$release_version" ] \
    || fail "$image_key must use the fixed local release tag $local_repo:$release_version"
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

for key in POSTGRES_USER POSTGRES_PASSWORD RABBITMQ_USER RABBITMQ_PASSWORD JWT_SECRET \
  JWT_EXPIRES_IN CORS_ORIGIN PROMOTE_FIRST_ADMIN WORKER_CONCURRENCY WORKER_MAX_CONCURRENCY; do
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print}' "$production_env_example")"
  [ -n "$value" ] || fail "$production_env_example is missing $key"
done
[ "$(awk -F= '$1 == "PROMOTE_FIRST_ADMIN" {print $2}' "$production_env_example")" = "false" ] \
  || fail "production environment example must disable first-admin promotion"
grep -Fxq '.env.production' .gitignore || fail ".env.production must be ignored"
grep -Fxq '!.env.production.example' .gitignore || fail ".env.production.example must remain trackable"

grep -Fq 'PROMOTE_FIRST_ADMIN: "false"' "$compose_file" \
  || fail "production first-admin promotion must be disabled"
grep -Fq 'formal_env_file="${FORMAL_ENV_FILE:-$release_repo_root/.env.production}"' scripts/release-common.sh \
  || fail "release tools must default to .env.production"
grep -Fq 'still contains a placeholder' scripts/release-preflight.sh \
  || fail "release preflight must reject production secret placeholders"
grep -Fq 'config --format json' scripts/release-preflight.sh \
  || fail "release preflight must inspect the resolved Compose configuration"
grep -Fq 'resolved Release Compose must force PROMOTE_FIRST_ADMIN=false' scripts/release-preflight.sh \
  || fail "release preflight must fail when first-admin promotion is enabled"
grep -Fq '*/*|*:latest|*@*)' scripts/release-preflight.sh \
  || fail "release preflight must reject registry, latest and digest application references"
grep -Fq 'image must include an explicit release tag' scripts/release-preflight.sh \
  || fail "release preflight must reject implicit latest image references"
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
grep -Fq 'Local Image ID' "$manifest_template" \
  || fail "manifest template must record local Image IDs"

grep -Fq 'Reject tracked runtime environment files' "$ci_workflow" \
  || fail "CI must reject tracked runtime environment files"
grep -Fq "git ls-files -- .env .env.production .env.staging 'deploy/releases/*.env'" "$ci_workflow" \
  || fail "CI must inspect the Git index for production runtime environment files"

echo "Release configuration checks passed (fixed local images, external data, ignored secrets, no registry or staging dependency)."
