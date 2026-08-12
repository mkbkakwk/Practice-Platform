#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_root/scripts/docker-socket-gid.sh"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

(
  docker() {
    [[ "${MSYS_NO_PATHCONV:-}" == "1" ]] || return 89
    [[ "$*" == *"--network none"* ]] || return 90
    [[ "$*" == *"--read-only"* ]] || return 91
    [[ "$*" == *"--user 65534:65534"* ]] || return 95
    [[ "$*" == *"--cap-drop ALL"* ]] || return 92
    [[ "$*" == *"--security-opt no-new-privileges"* ]] || return 93
    [[ "$*" == *"src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly"* ]] || return 94
    [[ "$*" == *"--entrypoint /bin/stat"* ]] || return 96
    printf '4242 0 srw-rw----\n'
  }
  configure_docker_socket_gid >/dev/null 2>&1
  [[ "$DOCKER_SOCKET_GID" == "4242" ]] || fail "non-zero socket GID was not propagated"
)

(
  docker() { printf 'not-a-gid 0 srw-rw----\n'; }
  if configure_docker_socket_gid >/dev/null 2>&1; then
    fail "non-numeric socket GID must fail closed"
  fi
)

(
  docker() { printf '123 0 -rw-rw----\n'; }
  if configure_docker_socket_gid >/dev/null 2>&1; then
    fail "non-socket metadata must fail closed"
  fi
)

(
  docker() { return 1; }
  if configure_docker_socket_gid >/dev/null 2>&1; then
    fail "socket inspection failure must not fall back to GID zero"
  fi
)

echo "Docker socket GID resolver tests: PASSED"
