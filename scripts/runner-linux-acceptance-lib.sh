#!/usr/bin/env bash

# Shared, side-effect-minimal helpers for the Linux acceptance orchestrator.
# This file is sourced by the host harness and its regression tests.

acceptance_unit_load_state() {
  local unit="$1"
  local state
  state="$(systemctl show --property=LoadState --value "$unit" 2>/dev/null)" || return 2
  [[ -n "$state" && "$state" != *$'\n'* && "$state" != *$'\r'* ]] || return 2
  printf '%s\n' "$state"
}

# Return 0 for every unit known to systemd (loaded, masked, error, and other
# non-empty states), 1 only for LoadState=not-found, and 2 when state cannot be
# determined. Callers must treat 2 as fail-closed, never as "does not exist".
acceptance_unit_exists() {
  local unit="$1"
  local state
  state="$(acceptance_unit_load_state "$unit")" || return 2
  [[ "$state" == "not-found" ]] && return 1
  return 0
}

acceptance_resources_removed() {
  local unit="$1"
  local cgroup="$2"
  local unit_rc
  if acceptance_unit_exists "$unit"; then
    return 1
  else
    unit_rc=$?
  fi
  [[ $unit_rc -eq 1 ]] || return 2
  [[ ! -e "$cgroup" ]]
}

remove_safe_jvm_attach_markers() {
  local repo="$1"
  local runner_dir="$repo/runner"
  local candidate name

  [[ -d "$runner_dir" && ! -L "$runner_dir" ]] || return 1
  while IFS= read -r -d '' candidate; do
    name="${candidate##*/}"
    if [[ ! "$name" =~ ^\.attach_pid[0-9]+$ ]]; then
      continue
    fi
    if [[ -L "$candidate" || ! -f "$candidate" ]]; then
      printf 'ERROR: unsafe JVM attach marker: runner/%s\n' "$name" >&2
      return 1
    fi
    rm -f -- "$candidate" || return 1
    printf 'Removed JVM attach runtime marker: runner/%s\n' "$name"
  done < <(find "$runner_dir" -mindepth 1 -maxdepth 1 -name '.attach_pid*' -print0)
}

prepare_acceptance_source_tree() {
  local repo="$1"
  local status

  remove_safe_jvm_attach_markers "$repo" || return 1
  status="$(git -c "safe.directory=$repo" -C "$repo" status --porcelain)" || return 1
  [[ -z "$status" ]]
}
