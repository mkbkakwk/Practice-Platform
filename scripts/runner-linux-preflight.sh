#!/usr/bin/env bash

# Read-only security capability check for the dedicated Linux Runner host.
# This script never installs packages, changes cgroups, or writes probe files.
set -u

failures=()
nsjail_path="${RUNNER_NSJAIL_PATH:-/usr/bin/nsjail}"
rootfs="${RUNNER_SANDBOX_ROOTFS:-/srv/oj-sandbox-runner/rootfs}"
workspace="${RUNNER_WORKSPACE_ROOT:-/run/oj-sandbox-runner/jobs}"
cgroup_root="${RUNNER_CGROUP_V2_MOUNT:-/sys/fs/cgroup/system.slice/oj-sandbox-runner.service}"
seccomp_policy="${RUNNER_SECCOMP_POLICY:-/etc/oj-sandbox-runner/nsjail-seccomp.policy}"
apparmor_preflight_profile="${RUNNER_APPARMOR_PREFLIGHT_PROFILE:-}"

fail() {
  failures+=("$1")
  printf 'FAIL  %s\n' "$1"
}

pass() {
  printf 'PASS  %s\n' "$1"
}

# Resolve a guest path one component at a time without allowing the host kernel
# to interpret absolute symlink targets outside the configured runtime root.
rootfs_path_exists() {
  local root="$1"
  local guest_path="$2"
  local root_abs component candidate target
  local symlink_count=0
  local max_symlinks=40
  local -a pending=()
  local -a resolved=()
  local -a target_parts=()

  [[ -d "$root" && ! -L "$root" ]] || return 1
  root_abs="$(CDPATH= cd -- "$root" 2>/dev/null && pwd -P)" || return 1
  [[ "$guest_path" == /* ]] || guest_path="/$guest_path"
  IFS='/' read -r -a pending <<< "$guest_path"

  while [[ ${#pending[@]} -gt 0 ]]; do
    component="${pending[0]}"
    pending=("${pending[@]:1}")
    case "$component" in
      ''|.)
        continue
        ;;
      ..)
        [[ ${#resolved[@]} -gt 0 ]] || return 1
        resolved=("${resolved[@]:0:$((${#resolved[@]} - 1))}")
        continue
        ;;
    esac

    candidate="$root_abs"
    local resolved_component
    for resolved_component in "${resolved[@]}"; do
      candidate+="/$resolved_component"
    done
    candidate+="/$component"

    if [[ -L "$candidate" ]]; then
      symlink_count=$((symlink_count + 1))
      [[ $symlink_count -le $max_symlinks ]] || return 1
      target="$(readlink -- "$candidate" 2>/dev/null)" || return 1
      [[ -n "$target" ]] || return 1
      target_parts=()
      IFS='/' read -r -a target_parts <<< "$target"
      if [[ "$target" == /* ]]; then
        resolved=()
      fi
      pending=("${target_parts[@]}" "${pending[@]}")
    elif [[ -e "$candidate" ]]; then
      if [[ ${#pending[@]} -gt 0 && ! -d "$candidate" ]]; then
        return 1
      fi
      resolved+=("$component")
    else
      return 1
    fi
  done

  return 0
}

echo "Runner Linux security preflight (read-only)"

if [[ "$(uname -s 2>/dev/null || true)" != "Linux" ]]; then
  fail "host is not Linux"
else
  pass "Linux kernel: $(uname -r)"
fi

kernel_text="$(uname -r 2>/dev/null || true) $(cat /proc/version 2>/dev/null || true)"
if [[ -e /.dockerenv ]]; then
  fail "ordinary Docker container is not an accepted Runner host"
elif [[ "${kernel_text,,}" == *microsoft* || "${kernel_text,,}" == *wsl* ]]; then
  fail "WSL/Docker Desktop is not an accepted security host"
else
  pass "dedicated-host check"
fi

if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
  fail "Runner service must be non-root"
else
  pass "Runner service is non-root"
fi

cap_eff="$(awk '/^CapEff:/ {print $2}' /proc/self/status 2>/dev/null || true)"
if [[ -z "$cap_eff" || ! "$cap_eff" =~ ^0+$ ]]; then
  fail "Runner service must have no effective capabilities"
else
  pass "effective capabilities are empty"
fi

for namespace in mnt pid net uts ipc user cgroup time; do
  if [[ -e "/proc/self/ns/$namespace" ]]; then
    pass "namespace available: $namespace"
  else
    fail "namespace unavailable: $namespace"
  fi
done

namespace_probe=(
  unshare --user --map-current-user --mount --pid --fork --net --ipc --uts true
)
if [[ -n "$apparmor_preflight_profile" ]]; then
  if command -v aa-exec >/dev/null 2>&1 \
    && aa-exec -p "$apparmor_preflight_profile" -- "${namespace_probe[@]}" >/dev/null 2>&1; then
    pass "unprivileged namespace creation"
  else
    fail "unprivileged namespace creation"
  fi
elif command -v unshare >/dev/null 2>&1 \
  && "${namespace_probe[@]}" >/dev/null 2>&1; then
  pass "unprivileged namespace creation"
else
  fail "unprivileged namespace creation"
fi

if [[ -x "$nsjail_path" ]] && "$nsjail_path" --help >/dev/null 2>&1; then
  pass "nsjail executable"
else
  fail "nsjail executable"
fi

if [[ "$(stat -fc %T /sys/fs/cgroup 2>/dev/null || true)" == "cgroup2fs" ]]; then
  pass "cgroup v2 mounted"
else
  fail "cgroup v2 is not mounted"
fi

if [[ -d "$cgroup_root" && ! -L "$cgroup_root" ]]; then
  controllers="$(cat "$cgroup_root/cgroup.controllers" 2>/dev/null || true)"
  enabled_controllers="$(cat "$cgroup_root/cgroup.subtree_control" 2>/dev/null || true)"
  for controller in cpu memory pids; do
    if [[ " $controllers " == *" $controller "* ]]; then
      pass "cgroup controller delegated: $controller"
    else
      fail "cgroup controller missing: $controller"
    fi
    if [[ " $enabled_controllers " == *" $controller "* ]]; then
      pass "cgroup controller enabled: $controller"
    else
      fail "cgroup controller not enabled: $controller"
    fi
  done
  if [[ -w "$cgroup_root" && -w "$cgroup_root/cgroup.subtree_control" ]]; then
    pass "cgroup root is delegated writable"
  else
    fail "cgroup root is not delegated writable"
  fi
  if [[ -r "$cgroup_root/cgroup.procs" && ! -s "$cgroup_root/cgroup.procs" ]]; then
    pass "delegated cgroup root has no processes"
  else
    fail "delegated cgroup root must be empty (use DelegateSubgroup)"
  fi
else
  fail "delegated cgroup root is unavailable or a symlink"
fi

if [[ -d "$rootfs" && ! -L "$rootfs" ]]; then
  rootfs_options="$(findmnt -n -o OPTIONS -T "$rootfs" 2>/dev/null || true)"
  if [[ ",$rootfs_options," == *,ro,* ]]; then
    pass "runtime rootfs is mounted read-only"
  else
    fail "runtime rootfs is not mounted read-only"
  fi
  for required in \
    dev proc tmp workspace \
    usr/bin/python3 usr/bin/node usr/bin/gcc usr/bin/g++ usr/bin/javac usr/bin/java \
    usr/lib/jvm/java-21-openjdk-amd64; do
    if rootfs_path_exists "$rootfs" "/$required"; then
      pass "rootfs path: /$required"
    else
      fail "rootfs path missing: /$required"
    fi
  done
else
  fail "runtime rootfs is unavailable or a symlink"
fi

if [[ -d "$workspace" && ! -L "$workspace" && -w "$workspace" ]]; then
  if [[ "$(findmnt -n -o FSTYPE -T "$workspace" 2>/dev/null || true)" == "tmpfs" ]]; then
    pass "workspace root is writable tmpfs"
  else
    fail "workspace root is not tmpfs"
  fi
else
  fail "workspace root is unavailable, unsafe, or not writable"
fi

if [[ -r "$seccomp_policy" && -f "$seccomp_policy" && ! -L "$seccomp_policy" ]]; then
  pass "seccomp policy is readable"
else
  fail "seccomp policy is unavailable or unsafe"
fi

if [[ ${#failures[@]} -eq 0 ]]; then
  echo "SUPPORTED"
  exit 0
fi

printf 'UNSUPPORTED (%d failed checks)\n' "${#failures[@]}"
exit 1
