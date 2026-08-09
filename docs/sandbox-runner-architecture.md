# Sandbox Runner architecture

## Current stage

Stage 3A establishes an execution abstraction and a versioned remote protocol:

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
non-root execution is a Stage 3B requirement. Docker Desktop is not used as a
substitute for that security boundary.

Stage 3A does not use a Docker socket, a privileged container, a host PID/network
namespace, nsjail, isolate, or host cgroup mounts.

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

## Authentication and logging

`RUNNER_TOKEN` is read from the environment and sent only in the `Authorization`
header. Real tokens must never be committed. Logs correlate `submissionId`,
`requestId`, language, status, and duration, but do not log source code, test input,
the Bearer token, passwords, or other secrets.

Private networking and the Bearer token are only the Stage 3A transport boundary.
mTLS can be evaluated when the dedicated Runner is deployed.

## Testing

All tests run in disposable Docker test services. Remote client tests use a local
mock HTTP server and do not create a persistent Runner container. Regression tests
retain Python, JavaScript, C, C++17, and Java behaviour through the legacy adapter.

Stage 4 still owns RabbitMQ acknowledgement reliability, retries, Outbox, and DLQ;
this refactor intentionally does not change those semantics.
