#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging-common.sh"

remove_volumes=false
case "${1:-}" in
  "") ;;
  --volumes) remove_volumes=true ;;
  *)
    echo "Usage: $0 [--volumes]" >&2
    exit 2
    ;;
esac
[[ $# -le 1 ]] || {
  echo "Usage: $0 [--volumes]" >&2
  exit 2
}

if [[ "$remove_volumes" == "true" ]]; then
  assert_safe_staging_volumes
  echo "Stopping $project_name and deleting only its three staging volumes"
  "${compose[@]}" down --remove-orphans --volumes
else
  echo "Stopping $project_name; staging data volumes will be preserved"
  "${compose[@]}" down --remove-orphans
fi
