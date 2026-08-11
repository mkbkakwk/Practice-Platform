#!/usr/bin/env bash

set -u

command_name="$(basename "$0")"
if [[ -n "${PREFLIGHT_TEST_LOG:-}" ]]; then
  printf '%s\n' "$command_name" "$@" >> "$PREFLIGHT_TEST_LOG"
fi

case "$command_name" in
  nsjail)
    [[ "${1:-}" == "--experimental_mnt" ]] || exit 64
    [[ "${2:-}" == "new" ]] || exit 64
    [[ "${3:-}" == "--help" ]] || exit 64
    [[ "$#" == "3" ]] || exit 64
    [[ "${MOCK_NSJAIL_NEW_MOUNT_API:-1}" == "1" ]] || exit 64
    printf '%s\n' "Mount API to use: 'new' (fsopen/fsmount), 'old' (mount syscall), or" \
      "'auto' (auto-detect based on kernel version). Default: 'old'"
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
