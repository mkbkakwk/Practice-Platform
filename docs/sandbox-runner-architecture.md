# Sandbox Runner architecture

## Current architecture

Student submissions follow one fail-closed path:

```text
RabbitMQ
  -> Worker pool (judge business rules; no Docker socket or language toolchains)
  -> RemoteSandboxClient
  -> POST /api/v1/jobs
  -> Runner (trusted Docker control plane)
  -> disposable compile/run containers
```

The Worker compares actual output with expected output and owns AC/WA. The Runner
only returns execution statuses and bounded output. Remote failures become `SE`;
the Worker never falls back to local execution.

The prior nsjail Stage 3B-2 implementation and its Ubuntu 15/15 evidence are
preserved at Git ref `archive/nsjail-stage3b2-15of15`. The experimental
Dockerized-nsjail work remains on its feature branch for reference. The formal
deployment path uses per-submission Docker containers.

## Trust boundaries

The Runner is a trusted control-plane service. It has access to the Docker socket,
which is equivalent to control of the Docker daemon. It must be isolated from
untrusted clients and protected by a random `RUNNER_TOKEN`. Neither the Worker nor
any student container receives the socket.

Each student container is created with a fixed, reviewed `HostConfig`:

- `Privileged=false`, no host PID/IPC/network namespace, and network mode `none`;
- all Linux capabilities dropped and `no-new-privileges` enabled;
- Docker's default seccomp policy remains active;
- non-root UID/GID `10001:10001` and a read-only root filesystem;
- memory plus swap, CPU, PID, workspace and `/tmp` limits;
- only a per-submission artifact volume and per-case input are mounted;
- fixed language image and fixed argv selected by a closed language enum.

Student source, stdin, expected output, compiler paths, shell commands and Docker
options cannot alter the control-plane command. The helper reads stdin from a
fixed `O_NOFOLLOW` path and then uses `execv`; no shell interpolation is involved.

## Execution lifecycle

One submission receives a unique Runner-owned label and artifact volume. C, C++
and Java compile once in a disposable compile container; Python and JavaScript use
the same validation boundary without a native artifact. Every testcase then runs
in a fresh container, so `/workspace`, `/tmp`, processes and output are isolated
between cases. The Runner streams bounded stdout/stderr and enforces wall time by
killing the disposable container.

Cleanup is mandatory on success, CE, RE, TLE, MLE, OLE and internal failure. The
Runner removes testcase/compile containers and the submission volume, retries
bounded cleanup operations, verifies no matching resources remain and returns
`SYSTEM_ERROR` if cleanup cannot be proven. Startup stale cleanup is restricted to
resources bearing the exact configured Runner-instance label.

## Resource and status handling

Runner status values are independent from platform verdicts:

- `OK`
- `COMPILE_ERROR`
- `RUNTIME_ERROR`
- `TIME_LIMIT_EXCEEDED`
- `MEMORY_LIMIT_EXCEEDED`
- `OUTPUT_LIMIT_EXCEEDED`
- `SYSTEM_ERROR`

TLE and OLE are authoritative forced terminations. MLE is classified using Docker
OOM state and post-mortem memory statistics; exit code 137 alone is not treated as
memory evidence. Ordinary non-zero exits remain RE. Result messages are bounded
metadata and do not consume the student's stdout/stderr allowance.

A fair semaphore bounds accepted Runner jobs. Formal Compose defaults to four.
Saturation returns HTTP 429; callers wait/retry rather than creating unbounded
threads or silently bypassing isolation. Worker replicas are competing RabbitMQ
consumers of the same queue, with prefetch one and manual acknowledgement.

## Protocol and authentication

The versioned endpoint is `POST /api/v1/jobs` with `Authorization: Bearer
<RUNNER_TOKEN>`. A request contains UUID `requestId`, a closed language enum,
source, limits, and ordered testcase inputs. It never contains command, shell,
argv, executable path or expected answers. Runner and Worker share contract JSON
fixtures under `test/fixtures/runner/`.

Both sides enforce request IDs, field completeness, order, size limits, timeouts,
known statuses and bounded responses. Tokens, source, stdin, stdout and stderr are
not written to normal logs.

## Validation

The complete Docker test entrypoint is:

```bash
./scripts/test-docker.sh
```

It includes ordinary Backend/Worker/Runner/Frontend tests, release/config checks,
the real Docker security suite, and a three-Worker competing-consumer test. The
security suite exercises five languages, CE/RE/TLE/MLE/OLE, fork/PID limits,
network and raw-socket isolation, capabilities, namespace and `/proc` isolation,
read-only filesystems, bounded `/tmp`, secret isolation, concurrency, and cleanup.
Test Compose projects are disposable and use exact labels; no global prune is used.

## Deployment

Build the five fixed sandbox images before starting the application services:

```bash
docker compose --profile sandbox-images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image
docker compose up -d --build --scale worker=3
```

Set `RUNNER_TOKEN` and `DOCKER_SOCKET_GID` in an ignored local environment file.
Only the Runner joins the Docker control plane. Student containers use network
mode `none`, so Worker-to-Runner HTTP is allowed while student-to-Runner, Backend,
database, RabbitMQ and Internet access is denied.
