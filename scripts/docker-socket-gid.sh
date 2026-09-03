#!/usr/bin/env bash

# Resolve the Docker socket ownership as it appears inside a Linux container.
# This is required on Docker Desktop as well as native Linux because the host
# filesystem view is not necessarily the same as the bind-mounted view.
configure_docker_socket_gid() {
  local metadata gid uid mode extra

  if ! metadata="$(MSYS_NO_PATHCONV=1 docker run --rm \
      --network none \
      --read-only \
      --user 65534:65534 \
      --cap-drop ALL \
      --security-opt no-new-privileges \
      --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock,readonly \
      --entrypoint /bin/stat \
      alpine:3.20 \
      -c '%g %u %A' /var/run/docker.sock)"; then
    echo "ERROR: unable to inspect Docker socket ownership" >&2
    return 1
  fi

  read -r gid uid mode extra <<<"$metadata"
  if [[ ! "$gid" =~ ^[0-9]+$ || ! "$uid" =~ ^[0-9]+$ \
      || ! "$mode" =~ ^s[-rwxStT]{9}$ || -n "${extra:-}" ]]; then
    echo "ERROR: invalid Docker socket ownership metadata" >&2
    return 1
  fi

  export DOCKER_SOCKET_GID="$gid"
  export DOCKER_SOCKET_UID="$uid"
  export DOCKER_SOCKET_MODE="$mode"
  echo "Docker socket (container view): uid=$uid gid=$gid mode=$mode" >&2
}

print_docker_socket_diagnostics() {
  echo "Docker socket (container view): uid=${DOCKER_SOCKET_UID:-unresolved} gid=${DOCKER_SOCKET_GID:-unresolved} mode=${DOCKER_SOCKET_MODE:-unresolved}" >&2
}
