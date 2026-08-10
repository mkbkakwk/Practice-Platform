#!/usr/bin/env bash

set -u

command_name="$(basename "$0")"
if [[ -n "${PREFLIGHT_TEST_LOG:-}" ]]; then
  printf '%s\n' "$command_name" "$@" >> "$PREFLIGHT_TEST_LOG"
fi

case "$command_name" in
  nsjail)
    [[ "${1:-}" == "--help" ]] || exit 64
    exit "${MOCK_NSJAIL_RC:-0}"
    ;;
  unshare)
    exit "${MOCK_UNSHARE_RC:-0}"
    ;;
  aa-exec)
    exit "${MOCK_AA_EXEC_RC:-0}"
    ;;
  *)
    exit 64
    ;;
esac
