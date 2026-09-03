#!/usr/bin/env sh

set -eu

compose_file="docker-compose.release.yml"
staging_compose_file="docker-compose.staging.yml"
staging_common="scripts/staging-common.sh"
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

for file in "$compose_file" "$staging_compose_file" "$staging_common" "$release_example" "$production_env_example" \
  "$manifest_template" "$release_workflow_doc" "$ci_workflow"; do
  [ -f "$file" ] || fail "missing $file"
done
[ ! -e "$publish_workflow" ] || fail "remote registry publishing workflow must be removed"

for script in scripts/release-common.sh scripts/release-metadata.sh scripts/release-preflight.sh scripts/release-t1-preflight.sh scripts/release-status.sh scripts/test-release-metadata.sh; do
  bash -n "$script" || fail "invalid Bash syntax in $script"
done
bash scripts/test-release-metadata.sh || fail "release metadata validation failed"

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
for key in RELEASE_BUILD_TIME EXPECTED_RUNNER_IMAGE_ID FORMAL_RUNNER_CONTAINER PREVIOUS_PRODUCTION_GIT_SHA PREVIOUS_PRODUCTION_FLYWAY_VERSION BACKUP_TOOL_GIT_SHA T1_PRODUCTION_RUNTIME_GIT_SHA; do
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print}' "$release_example")"
  [ -n "$value" ] || fail "$release_example is missing $key"
done
for image_spec in \
  "BACKEND_IMAGE:oj-backend" \
  "WORKER_IMAGE:oj-worker" \
  "RUNNER_IMAGE:oj-runner" \
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
  'image: ${RUNNER_IMAGE:?RUNNER_IMAGE is required}' \
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
  JWT_EXPIRES_IN CORS_ORIGIN PROMOTE_FIRST_ADMIN WORKER_CONCURRENCY WORKER_MAX_CONCURRENCY \
  RUNNER_TOKEN DOCKER_SOCKET_GID RUNNER_DOCKER_PYTHON_IMAGE RUNNER_DOCKER_JAVASCRIPT_IMAGE \
  RUNNER_DOCKER_C_IMAGE RUNNER_DOCKER_CPP_IMAGE RUNNER_DOCKER_JAVA_IMAGE; do
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
grep -Fq 'verify_image Runner' scripts/release-preflight.sh \
  || fail "release preflight must verify the Runner image"
grep -Fq 'RUNNER_TOKEN' scripts/release-preflight.sh \
  || fail "release preflight must require the Runner token"
grep -Fq 'metadata_is_full_git_sha "$release_git_sha"' scripts/release-preflight.sh \
  || fail "release preflight must require a full Git SHA"
grep -Fq 'T1_PRODUCTION_RUNTIME_GIT_SHA must match PREVIOUS_PRODUCTION_GIT_SHA' scripts/release-preflight.sh \
  || fail "release preflight must validate explicit T1 runtime provenance"
grep -Fq 'metadata_require_matching_production_runtime' scripts/release-t1-preflight.sh \
  || fail "T1 preflight must compare expected, observed, and declared production runtime identity"
grep -Fq 'metadata_is_utc_build_time "$release_build_time"' scripts/release-preflight.sh \
  || fail "release preflight must require an immutable UTC build time"
grep -Fq '*/*|*:latest|*@*)' scripts/release-preflight.sh \
  || fail "release preflight must reject registry, latest and digest application references"
grep -Fq 'image must include an explicit release tag' scripts/release-preflight.sh \
  || fail "release preflight must reject implicit latest image references"
if grep -Eq 'VITE_DEPLOY_ENV|VITE_BUILD_SHA' "$compose_file"; then
  fail "production release Compose must not inject the staging badge"
fi
grep -Fq 'JUDGE_EXECUTION_MODE: remote' "$compose_file" \
  || fail "release Worker must use remote Runner execution"
grep -Fq 'RUNNER_BASE_URL: http://runner:8080' "$compose_file" \
  || fail "release Worker must target the trusted Runner"
grep -Fq 'http://127.0.0.1:8081/api/readiness' "$compose_file" \
  || fail "release Worker must expose a readiness healthcheck"
grep -Fq 'http://127.0.0.1:8080/api/readiness' "$compose_file" \
  || fail "release Runner must expose a readiness healthcheck"
grep -Fq 'deployEnvironment === "staging"' frontend/src/components/Navbar.tsx \
  || fail "staging badge is not explicitly gated"

grep -Fq 'STAGING_FULL_GIT_SHA="${STAGING_FULL_GIT_SHA:-$(git -C "$repo_root" rev-parse HEAD)}"' "$staging_common" \
  || fail "staging must derive immutable application metadata from the full source SHA"
grep -Fq 'STAGING_GIT_SHA="${STAGING_GIT_SHA:-$(git -C "$repo_root" rev-parse --short=7 HEAD)}"' "$staging_common" \
  || fail "staging short image tags must remain distinct from application metadata"
grep -Fq 'STAGING_BUILD_TIME="${STAGING_BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"' "$staging_common" \
  || fail "staging must inject immutable UTC build metadata"
grep -Fq 'APP_GIT_SHA: ${STAGING_FULL_GIT_SHA:?STAGING_FULL_GIT_SHA is required}' "$staging_compose_file" \
  || fail "staging Backend must receive the full source revision"
grep -Fq 'APP_BUILD_TIME: ${STAGING_BUILD_TIME:?STAGING_BUILD_TIME is required}' "$staging_compose_file" \
  || fail "staging Backend must receive immutable build metadata"
grep -Fq 'VITE_BUILD_SHA: ${STAGING_GIT_SHA:-local}' "$staging_compose_file" \
  || fail "staging frontend badge must retain the short image tag"
grep -Fq 'org.opencontainers.image.revision: "${STAGING_FULL_GIT_SHA:?STAGING_FULL_GIT_SHA is required}"' "$staging_compose_file" \
  || fail "staging images must carry the full source revision"
grep -Fq 'org.opencontainers.image.created: "${STAGING_BUILD_TIME:?STAGING_BUILD_TIME is required}"' "$staging_compose_file" \
  || fail "staging images must carry immutable build metadata"

for field in 'Release Version' 'Release Git SHA' 'Main Merge Commit' 'Previous Production Git SHA' 'Backup Tool Git SHA' 'T1 Production Runtime Git SHA' \
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
