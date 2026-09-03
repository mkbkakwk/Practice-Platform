#!/usr/bin/env bash

# Shared validation for immutable release metadata. These checks intentionally
# validate values injected at build/deploy time; runtime containers need not
# contain a Git checkout.

metadata_is_full_git_sha() {
  [[ "${1:-}" =~ ^[0-9a-f]{40}$ ]]
}

metadata_require_matching_production_runtime() {
  local expected="$1" observed="$2" declared="$3"
  metadata_is_full_git_sha "$expected" && metadata_is_full_git_sha "$observed" \
    && metadata_is_full_git_sha "$declared" \
    && [[ "$expected" == "$observed" && "$expected" == "$declared" ]]
}

metadata_is_utc_build_time() {
  [[ "${1:-}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]
}
