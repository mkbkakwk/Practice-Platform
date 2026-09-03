# Security policy

## Reporting a vulnerability

Do not open a public issue for a vulnerability that could expose credentials,
student data, host access, or sandbox escape details. Contact the repository owner
privately with the affected commit, reproduction steps, impact, and any suggested
mitigation. Do not test against Production or Staging without explicit approval.

## Sandbox threat model

Student source code and testcase input are untrusted. The supported deployment
routes them through the Worker to the authenticated Runner. The Worker is trusted
business logic and has no Docker socket or student language toolchains. The Runner
is a trusted Docker control plane and is the only service with the Docker socket.

Docker socket access is highly privileged: compromise of the Runner can imply
control of the Docker daemon and host. Keep the Runner private, use a random Bearer
token, do not publish its port, and never expose the socket to Worker or student
containers.

Student containers are disposable, non-root, read-only, networkless, capability-
free, `no-new-privileges` processes under Docker's default seccomp policy. CPU,
memory/swap, PID count, output, wall time, workspace and `/tmp` are bounded. Fixed
language images and argv are selected from a closed enum; submitted data cannot
specify shell commands or Docker options.

The Runner must fail closed. Runner outage, invalid protocol responses, resource
cleanup failures, or saturation never cause a fallback to local Worker execution.

## Operational requirements

- Keep `RUNNER_TOKEN`, database passwords and JWT secrets out of Git and logs.
- Do not use `privileged`, host PID/network, `seccomp=unconfined`, or mount the
  Docker socket into Worker/student containers.
- Build and pin reviewed sandbox images; do not use `latest` for releases.
- Run `./scripts/test-docker.sh` before deployment. Security acceptance must have
  zero failures, errors and skips.
- Remove only resources carrying the exact Runner/test labels. Never use global
  prune as part of application cleanup.
- Production (`oj`) and Staging (`practice-platform-staging`) are separate; testing
  one is not authorization to modify the other.

The archived nsjail Stage 3B-2 evidence remains at
`archive/nsjail-stage3b2-15of15`. It is historical evidence, not the formal current
deployment path.
