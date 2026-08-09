#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/release-common.sh"

release_require_command docker
release_require_command git
release_require_command curl
docker compose version >/dev/null 2>&1 || release_die "docker compose is required"

release_require_file "$release_compose_file" "release Compose file"
release_require_file "$release_env_file" "local release metadata"
release_require_file "$formal_env_file" "production environment file"

release_keys=(
  RELEASE_VERSION RELEASE_TAG RELEASE_GIT_SHA RELEASE_MAIN_SHA RELEASE_FLYWAY_VERSION POSTGRES_DB
  POSTGRES_IMAGE RABBITMQ_IMAGE BACKEND_IMAGE WORKER_IMAGE FRONTEND_IMAGE
  EXPECTED_BACKEND_IMAGE_ID EXPECTED_WORKER_IMAGE_ID EXPECTED_FRONTEND_IMAGE_ID
  EXPECTED_OCI_VERSION FORMAL_POSTGRES_VOLUME FORMAL_RABBITMQ_VOLUME
  FORMAL_DOCS_VOLUME FORMAL_NETWORK FORMAL_POSTGRES_CONTAINER
  FORMAL_RABBITMQ_CONTAINER FORMAL_BACKEND_CONTAINER FORMAL_WORKER_CONTAINER
  FORMAL_FRONTEND_CONTAINER FORMAL_FRONTEND_PORT POSTGRES_LOGICAL_BACKUP
  POSTGRES_GLOBALS_BACKUP POSTGRES_BASE_BACKUP RABBITMQ_DEFINITIONS_BACKUP DOCS_BACKUP
)
for key in "${release_keys[@]}"; do
  release_require_env_key "$release_env_file" "$key"
done

secret_keys=(
  POSTGRES_USER POSTGRES_PASSWORD RABBITMQ_USER RABBITMQ_PASSWORD
  JWT_SECRET CORS_ORIGIN
)
for key in "${secret_keys[@]}"; do
  release_require_env_key "$formal_env_file" "$key"
  secret_value="$(release_env_value "$formal_env_file" "$key")"
  case "${secret_value,,}" in
    replace-with*|change-me*) release_die "$key still contains a placeholder" ;;
  esac
done
unset secret_value

jwt_secret="$(release_env_value "$formal_env_file" JWT_SECRET)"
[[ ${#jwt_secret} -ge 32 ]] || release_die "JWT_SECRET must contain at least 32 characters"
unset jwt_secret

promote_first_admin="$(release_env_value "$formal_env_file" PROMOTE_FIRST_ADMIN)"
case "${promote_first_admin:-false}" in
  false|FALSE|0) ;;
  *)
    echo "WARN: legacy environment requests PROMOTE_FIRST_ADMIN, but release Compose forces it to false" >&2
    ;;
esac

release_tag="$(release_env_value "$release_env_file" RELEASE_TAG)"
release_git_sha="$(release_env_value "$release_env_file" RELEASE_GIT_SHA)"
release_main_sha="$(release_env_value "$release_env_file" RELEASE_MAIN_SHA)"
release_flyway_version="$(release_env_value "$release_env_file" RELEASE_FLYWAY_VERSION)"

git -C "$release_repo_root" cat-file -e "$release_git_sha^{commit}" 2>/dev/null \
  || release_die "release Git SHA does not exist locally"
[[ "$(git -C "$release_repo_root" cat-file -t "refs/tags/$release_tag" 2>/dev/null)" == "tag" ]] \
  || release_die "$release_tag must be an annotated tag"
[[ "$(git -C "$release_repo_root" rev-list -n 1 "$release_tag")" == "$release_git_sha" ]] \
  || release_die "$release_tag does not resolve to RELEASE_GIT_SHA"
git -C "$release_repo_root" merge-base --is-ancestor "$release_git_sha" "$release_main_sha" \
  || release_die "release Git SHA is not in RELEASE_MAIN_SHA history"

verify_image() {
  local component="$1"
  local image_ref="$2"
  local expected_id="$3"
  local actual_id revision version
  case "${image_ref,,}" in
    */*|*:latest|*@*)
      release_die "$component must use a local immutable release tag: $image_ref"
      ;;
  esac
  [[ "$image_ref" == *:* ]] \
    || release_die "$component image must include an explicit release tag"
  actual_id="$(docker image inspect --format '{{.Id}}' "$image_ref" 2>/dev/null)" \
    || release_die "$component image is missing: $image_ref"
  [[ "$actual_id" == "$expected_id" ]] \
    || release_die "$component image ID does not match the release manifest"
  revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image_ref")"
  version="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$image_ref")"
  digests="$(docker image inspect --format '{{join .RepoDigests ","}}' "$image_ref")"
  [[ "$revision" == "$release_git_sha" ]] \
    || release_die "$component OCI revision does not match RELEASE_GIT_SHA"
  [[ "$version" == "$(release_env_value "$release_env_file" EXPECTED_OCI_VERSION)" ]] \
    || release_die "$component OCI version does not match EXPECTED_OCI_VERSION"
  echo "  $component: $image_ref -> $actual_id (revision verified)"
}

echo "Verifying immutable release images"
verify_image Backend \
  "$(release_env_value "$release_env_file" BACKEND_IMAGE)" \
  "$(release_env_value "$release_env_file" EXPECTED_BACKEND_IMAGE_ID)"
verify_image Worker \
  "$(release_env_value "$release_env_file" WORKER_IMAGE)" \
  "$(release_env_value "$release_env_file" EXPECTED_WORKER_IMAGE_ID)"
verify_image Frontend \
  "$(release_env_value "$release_env_file" FRONTEND_IMAGE)" \
  "$(release_env_value "$release_env_file" EXPECTED_FRONTEND_IMAGE_ID)"

echo "Verifying external production resources"
for volume_key in FORMAL_POSTGRES_VOLUME FORMAL_RABBITMQ_VOLUME FORMAL_DOCS_VOLUME; do
  volume="$(release_env_value "$release_env_file" "$volume_key")"
  docker volume inspect "$volume" >/dev/null 2>&1 || release_die "$volume_key does not exist"
  echo "  $volume_key: present"
done
formal_network="$(release_env_value "$release_env_file" FORMAL_NETWORK)"
docker network inspect "$formal_network" >/dev/null 2>&1 || release_die "FORMAL_NETWORK does not exist"
echo "  FORMAL_NETWORK: present"

echo "Verifying recovery files"
for backup_key in POSTGRES_LOGICAL_BACKUP POSTGRES_GLOBALS_BACKUP POSTGRES_BASE_BACKUP RABBITMQ_DEFINITIONS_BACKUP DOCS_BACKUP; do
  backup_path="$(release_env_value "$release_env_file" "$backup_key")"
  case "${backup_path,,}" in
    replace-with*) release_die "$backup_key still contains a placeholder" ;;
  esac
  release_require_file "$backup_path" "$backup_key"
  echo "  $backup_key: present"
done

export COMPOSE_DISABLE_ENV_FILE=1
docker compose \
  --project-name oj \
  --env-file "$formal_env_file" \
  --env-file "$release_env_file" \
  -f "$release_compose_file" \
  config --quiet
resolved_release_config="$(docker compose \
  --project-name oj \
  --env-file "$formal_env_file" \
  --env-file "$release_env_file" \
  -f "$release_compose_file" \
  config --format json)"
printf '%s' "$resolved_release_config" \
  | grep -Eq '"PROMOTE_FIRST_ADMIN"[[:space:]]*:[[:space:]]*"false"' \
  || release_die "resolved Release Compose must force PROMOTE_FIRST_ADMIN=false"
unset resolved_release_config
echo "Release Compose interpolation: valid"
echo "Resolved PROMOTE_FIRST_ADMIN: false"

db_container="$(release_env_value "$release_env_file" FORMAL_POSTGRES_CONTAINER)"
rabbit_container="$(release_env_value "$release_env_file" FORMAL_RABBITMQ_CONTAINER)"
backend_container="$(release_env_value "$release_env_file" FORMAL_BACKEND_CONTAINER)"
worker_container="$(release_env_value "$release_env_file" FORMAL_WORKER_CONTAINER)"
frontend_container="$(release_env_value "$release_env_file" FORMAL_FRONTEND_CONTAINER)"

for container in "$db_container" "$rabbit_container" "$backend_container" "$worker_container" "$frontend_container"; do
  [[ "$(release_container_state "$container")" == "running" ]] \
    || release_die "production container is not running: $container"
done
[[ "$(release_container_health "$db_container")" == "healthy" ]] \
  || release_die "PostgreSQL is not healthy"
[[ "$(release_container_health "$rabbit_container")" == "healthy" ]] \
  || release_die "RabbitMQ is not healthy"

flyway_version="$(docker exec -e PGOPTIONS='-c default_transaction_read_only=on' "$db_container" \
  sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"')"
[[ "$flyway_version" == "$release_flyway_version" ]] \
  || release_die "Flyway version is $flyway_version, expected $release_flyway_version"

frontend_port="$(release_env_value "$release_env_file" FORMAL_FRONTEND_PORT)"
curl --fail --silent --show-error "http://127.0.0.1:$frontend_port/" >/dev/null
health_json="$(curl --fail --silent --show-error "http://127.0.0.1:$frontend_port/api/health")"
[[ "$health_json" == *'"ok":true'* ]] || release_die "Backend health did not return ok=true"

echo "Release preflight passed:"
echo "  tag: $release_tag"
echo "  source: $release_git_sha"
echo "  Flyway: V$flyway_version"
echo "  production health: OK"
echo "No containers, images, networks, volumes, or database rows were modified."
