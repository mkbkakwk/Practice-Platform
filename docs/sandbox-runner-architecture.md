# Sandbox Runner architecture

## Delivery stages

- **Stage 3A — complete:** Worker execution abstraction and versioned remote
  protocol.
- **Stage 3B-1 — complete:** independent Runner HTTP service, authentication,
  validation, bounded job orchestration, and the `SandboxExecutor` boundary.
- **Stage 3B-2 — implementation complete; Linux acceptance pending:** dedicated Linux isolation using namespaces,
  cgroup v2, syscall filtering, non-root execution, a read-only filesystem, and
  network isolation.
- **Stage 3C — not started:** end-to-end integration and adversarial validation.

Stage 3A established this Worker-side path:

```text
RabbitMQ
  -> JudgeConsumer
  -> JudgeService (business verdicts and answer comparison)
  -> SandboxClient
       -> LegacyLocalSandboxClient
       -> RemoteSandboxClient -> POST /api/v1/jobs
```

`JudgeService` no longer knows compiler names, runtime commands, temporary-directory
layout, or process APIs. It sends source code and inputs to a `SandboxClient`, then
maps execution statuses to platform verdicts. Expected answers stay in the Worker;
the Runner is an execution engine, not the owner of judging rules.

## Security status

`LegacyLocalSandboxClient` is **not a security sandbox**. It preserves the existing
in-Worker execution behaviour while the remote boundary is introduced. It must not
be treated as isolation for hostile code.

The remote protocol alone also does not provide isolation. A dedicated Linux Runner
with namespaces, cgroups, syscall filtering, a read-only filesystem, no network, and
non-root execution is a Stage 3B-2 requirement. Docker Desktop is not used as a
substitute for that security boundary.

Stages 3A and 3B-1 do not use a Docker socket, a privileged container, a host
PID/network namespace, nsjail, isolate, or host cgroup mounts.

## Independent Runner service (Stage 3B-1)

The Java 21/Spring Boot service in `runner/` is an independent process with no
PostgreSQL, RabbitMQ, Flyway, user JWT, or business-data dependency:

```text
RunnerController
  -> RunnerJobService
       -> RunnerRequestValidator
       -> JobConcurrencyLimiter
       -> SandboxExecutor
```

`POST /api/v1/jobs` implements the Stage 3A contract. `GET /api/health` reports
HTTP liveness separately from `sandboxAvailable`; `ok=true` does not mean that a
secure execution boundary exists.

The default `UnavailableSandboxExecutor` never starts student code. It returns a
controlled `SYSTEM_ERROR` and reports `sandboxAvailable=false`. The deterministic
fake executor is selected only by the disposable `runner-contract-test` profile.
It exists to exercise Worker-to-Runner HTTP compatibility and is not a deployment
executor. `LinuxSandboxExecutor` is selected only by
`RUNNER_SANDBOX_MODE=linux`. The default remains unavailable, so merely building
or deploying the service does not enable student execution.

**Runner Service != Sandbox Security Boundary.** A Runner Docker container alone
does not make execution of hostile code safe.

The Stage 3B-1 service image itself runs as the unprivileged UID/GID 10001 and has
no shell-based entrypoint. This limits the HTTP service process but is not a
substitute for the per-job isolation that Stage 3B-2 must provide.

## Execution modes

The Worker supports two explicit modes:

- `legacy-local` (default): compatibility behaviour; no Runner configuration needed.
- `remote`: only the remote client is created. `RUNNER_BASE_URL` and `RUNNER_TOKEN`
  are mandatory at startup.

Remote mode never falls back to local execution. If the Runner is unavailable or
violates the protocol, the submission receives a controlled system error (`SE`).
This fail-closed rule prevents an isolation outage from silently becoming direct
execution inside the Worker.

Production and Staging remain on the default `legacy-local` mode until a dedicated
Runner has passed Stage 3B/3C acceptance. Merging Stage 3A alone does not switch a
running environment.

## Versioned protocol

The Worker sends one compile-once job to:

```text
POST /api/v1/jobs
Authorization: Bearer <RUNNER_TOKEN>
Content-Type: application/json
```

The request contains:

```json
{
  "requestId": "UUID",
  "language": "CPP17",
  "sourceCode": "...",
  "limits": {
    "compileTimeMs": 10000,
    "runTimeMs": 2000,
    "memoryMb": 256,
    "outputLimitBytes": 1048576
  },
  "cases": [
    { "caseId": "1", "stdin": "..." }
  ]
}
```

The language is a closed enum: `PYTHON`, `JAVASCRIPT`, `C`, `CPP17`, or `JAVA`.
The request has no command, shell, compiler argument, executable path, or expected
answer fields. A future Runner maps the enum to a trusted execution profile.

The response contains a compile result and an ordered prefix of case results:

```json
{
  "requestId": "UUID",
  "compile": {
    "status": "OK",
    "exitCode": 0,
    "stderr": "",
    "timeMs": 25,
    "message": ""
  },
  "cases": [
    {
      "caseId": "1",
      "status": "OK",
      "exitCode": 0,
      "stdout": "...",
      "stderr": "",
      "timeMs": 12,
      "memoryKb": 8192,
      "message": ""
    }
  ],
  "message": ""
}
```

Execution statuses are independent of platform verdicts:

- `OK`
- `COMPILE_ERROR`
- `RUNTIME_ERROR`
- `TIME_LIMIT_EXCEEDED`
- `MEMORY_LIMIT_EXCEEDED`
- `OUTPUT_LIMIT_EXCEEDED`
- `SYSTEM_ERROR`

For example, Runner `OK` is compared with the expected output by the Worker to
produce `AC` or `WA`; `COMPILE_ERROR` maps to `CE`; and an unavailable Runner maps
to `SE`.

## Protocol validation and limits

Both clients validate UUID request IDs, the language whitelist, positive limits,
case IDs, source size, input size, and case count. The remote client additionally
enforces:

- connect and request/read timeouts;
- encoded request and streamed response byte limits;
- per-case combined stdout/stderr limits;
- compile stderr limits;
- exact request ID matching;
- ordered, unique case IDs;
- known statuses and structurally complete results;
- no HTTP redirects.

Connection failures, timeout, non-2xx status, invalid JSON, missing fields, unknown
status, request ID mismatch, and oversized output all fail closed as `SE`.

The Runner independently validates the same contract instead of trusting the
Worker. Its initial server-side ceilings are centrally configurable:

- request body: 4 MiB (`RUNNER_MAX_REQUEST_BYTES`), enforced while reading HTTP;
- source: 1 MiB (`RUNNER_MAX_SOURCE_BYTES`);
- each stdin: 1 MiB (`RUNNER_MAX_STDIN_BYTES`);
- cases: 1,000 (`RUNNER_MAX_CASES`);
- compile wall limit requested by a job: at most 60 seconds;
- run wall limit requested by a job: at most 10 seconds;
- memory requested by a job: at most 2,048 MiB;
- per-result output requested by a job: at most 16 MiB.

The service rejects non-positive or excessive limits, duplicate/invalid case IDs,
unknown JSON fields, non-whitelisted languages, and executor responses that exceed
the job's output limit. A fair semaphore limits concurrent jobs; the default is 2
and saturation returns HTTP 429 without creating another execution thread.

## Authentication and logging

`RUNNER_TOKEN` is read from the environment and sent only in the `Authorization`
header. Real tokens must never be committed. Logs correlate `submissionId`,
`requestId`, language, status, and duration, but do not log source code, test input,
the Bearer token, passwords, or other secrets.

Private networking and the Bearer token are only the Stage 3A transport boundary.
mTLS can be evaluated when the dedicated Runner is deployed.

The Runner refuses to start without a non-blank `RUNNER_TOKEN`. Token comparison
uses a constant-time digest comparison. Missing or incorrect credentials return a
minimal 401 response; the token and Authorization header are never logged or
returned in an error body.

## Testing

All tests run in disposable Docker test services. Shared JSON fixtures in
`test/fixtures/runner/` are parsed and re-serialized by both Worker and Runner.
The HTTP contract test starts a temporary Runner with the test-only fake executor,
sends a real authenticated request through `RemoteSandboxClient`, then removes the
container and network. No long-running Runner is added to Production or Staging.
Regression tests retain Python, JavaScript, C, C++17, and Java behaviour through
the legacy adapter.

## Linux isolation implementation (Stage 3B-2)

The Linux path is deliberately a direct service on a dedicated Linux host or VM:

```text
RunnerJobService
  -> LinuxSandboxExecutor
       -> LanguageCommandResolver (fixed argv only)
       -> NsJailConfigWriter
       -> NsJailLauncher -> /usr/bin/nsjail --config ... -- <fixed argv>
```

`NsJailLauncher` is the only production class that uses `ProcessBuilder`. It can
start only the configured nsjail binary. There is no `bash -c`, `sh -c`, command
interpolation, Docker socket, privileged container, host PID/network namespace,
or `seccomp=unconfined` fallback. The HTTP contract still accepts only the five
language enums, source, stdin, and limits.

Compilation and every case execute in separate nsjail invocations. Source is
written once into a random per-job directory (`0700`), compiled once, and the
artifact is reused for ordered cases. C/C++/Java compilation, Python syntax
checking, and Node syntax checking are all isolated. Every outcome uses a `finally`
cleanup that does not follow student-created symbolic links.

Each invocation enables mount, PID, network, UTS, IPC, user, cgroup, and time
namespaces. The network namespace has no loopback interface and the seccomp policy
also denies `socket`. The mount tree consists of a read-only minimal language
rootfs, one writable `/workspace`, bounded tmpfs `/tmp`, minimal `/dev` devices,
and isolated read-only `/proc`. Student UID/GID default to `65534:65534`, inherited
environment and capabilities are cleared, and `no_new_privs` is enforced.

nsjail creates an execution cgroup under a systemd-delegated cgroup-v2 root. The
configuration applies `memory.max`, `memory.swap.max=0`, `pids.max`, and `cpu.max`.
The outer watchdog enforces the requested wall clock limit, monitors that exact
execution cgroup, bounds stdout+stderr while streaming, bounds workspace bytes and
file count, and kills the whole nsjail process tree on TLE/MLE/OLE or infrastructure
failure. A cleanup failure becomes `SYSTEM_ERROR`, never a successful result.

Java heap, metaspace, direct-memory, stack, and active-processor flags are derived
from the already validated request limit and remain below the cgroup ceiling. Node
receives a derived old-space limit. Profiles contain fixed absolute compiler and
runtime argv plus a minimal `PATH`, locale, home, and Java home; request fields
cannot change any executable or environment path.

At startup, Linux mode checks nsjail, namespaces, a read-only runtime root, tmpfs
workspace, the project seccomp policy, an empty delegated cgroup root with enabled
cpu/memory/pids controllers, required language runtimes, non-root identity, and an
empty effective capability set. It then executes a known non-student nsjail
self-test through the same path. Any failure leaves `sandboxAvailable=false`; there
is no unsafe fallback.

The example systemd service uses `Delegate=cpu memory pids` and
`DelegateSubgroup=runner` (systemd 254+) so the service remains unprivileged while
the delegated root is empty. Hosts that require root, `privileged`, host cgroups,
host namespaces, or a Docker socket are unsupported.

## Linux security acceptance

`scripts/runner-linux-preflight.sh` is read-only and prints exactly `SUPPORTED` or
`UNSUPPORTED`. `scripts/test-runner-linux.sh` refuses to run the security suite
unless preflight succeeds; an unsupported host reports `Linux isolation tests: NOT
RUN` and returns non-zero instead of silently skipping.

Ubuntu 24.04 hosts should retain AppArmor's global unprivileged-user-namespace
restriction. A provisioned and audited per-service profile can be selected for the
read-only namespace probe with
`RUNNER_APPARMOR_PREFLIGHT_PROFILE=oj-runner-preflight`; the profile name is passed
as one `aa-exec` argument, never through a shell command. This repository does not
create profiles or modify `/etc` or sysctls.

The Linux-only Maven profile covers all five languages, compile/runtime failures,
TLE, cgroup MLE, bounded stdout and stderr OLE, a fork bomb, background descendants,
student UID/GID and capabilities, environment/secret reads, isolated `/proc`, DNS
and localhost, mount/ptrace/raw socket attempts, read-only root writes, bounded
tmpfs, Runner health after attacks, and residual process/workspace/cgroup checks.

The current Windows Docker Desktop workspace is useful only for unit, protocol,
service, and configuration tests. It is explicitly rejected by preflight. Until
the Linux-only suite passes on the dedicated host, Stage 3B-2 is **not accepted**
and the Runner must not be connected to Staging or Production.

Stage 4 still owns RabbitMQ acknowledgement reliability, retries, Outbox, and DLQ;
this refactor intentionally does not change those semantics.
