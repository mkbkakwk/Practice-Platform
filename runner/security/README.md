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

Only a `SUPPORTED` result permits the Linux-only acceptance suite:

```bash
./scripts/test-runner-linux.sh
```

The project seccomp policy is a small explicit deny layer over namespace, cgroup,
filesystem, no-capability, and no-network isolation. Changes to it require the
Linux adversarial suite; do not replace it with an unreviewed allowlist.
