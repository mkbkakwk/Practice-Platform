# Dedicated Linux Runner security assets

These files target a dedicated Linux host or VM. Docker Desktop and an ordinary
container are deliberately rejected by the preflight and are not security
acceptance environments.

The runtime root at `/srv/oj-sandbox-runner/rootfs` must be prepared outside the
application, mounted read-only, and contain only the runtime files required by the
five trusted language profiles. Its `/proc`, `/tmp`, and `/workspace` directories
are mount points populated by nsjail. It must not contain the Runner token, source
repository, Production/Staging files, database credentials, or a Docker socket.

The Runner service is non-root and has an empty capability set. The example
systemd unit delegates only `cpu`, `memory`, and `pids` cgroup-v2 controllers.
`DelegateSubgroup=runner` requires systemd 254 or later and leaves the unit's
delegated cgroup root empty for nsjail-created per-execution cgroups. Hosts that
cannot meet this requirement are unsupported; do not compensate with root,
`privileged`, a host PID/network namespace, `seccomp=unconfined`, or a Docker
socket.

Provisioning the host/rootfs is intentionally outside this repository's automatic
scripts. Run the read-only preflight first:

```bash
./scripts/runner-linux-preflight.sh
```

On Ubuntu 24.04, keep
`kernel.apparmor_restrict_unprivileged_userns=1`. If host provisioning supplies a
dedicated AppArmor profile containing the required `userns,` permission, run the
namespace probe through that profile without weakening the global policy:

```bash
export RUNNER_APPARMOR_PREFLIGHT_PROFILE=oj-runner-preflight
./scripts/runner-linux-preflight.sh
```

The project does not create or modify AppArmor profiles. Supplying and auditing
the dedicated host profile is a deployment responsibility. Globally disabling
Ubuntu's unprivileged-user-namespace restriction is not recommended or supported.

The shell preflight is a read-only, pre-initialization host and delegation
capability check. It requires `cpu`, `memory`, and `pids` to be available, the
delegated root and `cgroup.subtree_control` to be writable by the Runner identity,
and the delegated root to contain no processes. Controllers may still be disabled
at this point. In Linux mode, Java's `DelegatedCgroupControllerInitializer` enables
missing controllers and verifies them by reading `cgroup.subtree_control` back;
`LinuxSandboxPreflight` runs afterwards via `@DependsOn` and requires the verified
post-initialization state. Neither the shell preflight nor the acceptance shell
orchestrator manually writes to the cgroup hierarchy.

Only a `SUPPORTED` result permits the Linux-only acceptance suite:

```bash
sudo --preserve-env=RUNNER_APPARMOR_PREFLIGHT_PROFILE \
  ./scripts/test-runner-linux.sh
```

The host-side script does not run Maven as root. It archives only the committed
`runner/` sources, Runner contract fixtures, and required scripts into a
short-lived directory under `/run/oj-sandbox-acceptance`, then starts
`oj-sandbox-acceptance.service` as a
transient systemd unit with `User=ojrunner`, the production security properties,
and its own delegated cgroup root. `ProtectHome=yes` remains enabled. Maven uses
the persistent dependency-only repository
`/var/cache/oj-sandbox-acceptance/m2` and never reads `/home/tu/.m2`. The cache
parent is `root:root/0755`; the repository is `ojrunner:ojrunner/0700`. Existing
symlinks, non-directories, or mismatched ownership/modes fail closed rather than
being repaired silently. Source staging remains ephemeral and cleanup deliberately
preserves only this dependency cache. The unit receives a private tmpfs workspace at
`/run/oj-sandbox-runner/jobs`; systemd unmounts it when the unit exits.
The staging tree intentionally remains on the host's `noexec` `/run` mount.
Trusted staged shell files are read with the host `/usr/bin/bash` interpreter;
the harness never disables `noexec` or directly executes a staged script.
The transient unit does not set `RUNNER_SANDBOX_MODE` for the Maven process:
ordinary Surefire tests retain their default fail-closed executor semantics, while
the Failsafe `linux-security` profile exactly includes
`LinuxSandboxSecurityIT.java`, sets Linux mode, and keeps `failIfNoTests=true` for
the real isolation suite.

Before checking that the committed tree is clean, the host orchestrator may
remove only a JVM Attach runtime marker matching the exact regular-file path
`runner/.attach_pid[0-9]+`. Symlinks, non-numeric names, markers outside that
directory, and every other tracked or untracked change still reject acceptance.

`ProtectKernelTunables` and `ProtectKernelLogs` are read from the effective
`oj-sandbox-runner.service` configuration so host-specific proc compatibility
drop-ins are reproduced without changing them. The acceptance unit never uses
the production service cgroup or JVM. Success is printed only after the unit,
staging tree, workspace mount, and acceptance cgroup have been cleaned.
Cleanup determines transient-unit existence from the explicit systemd
`LoadState`; only `not-found` means the unit has already been collected. A
successful `systemctl show` process exit by itself is not treated as evidence
that a unit still exists.

The project seccomp policy is a small explicit deny layer over namespace, cgroup,
filesystem, no-capability, and no-network isolation. Changes to it require the
Linux adversarial suite; do not replace it with an unreviewed allowlist.
