# Judge message reliability

## Guarantees and source of truth

The judge pipeline deliberately provides **at-least-once delivery**, not
exactly-once delivery. PostgreSQL is the authoritative source of submission
state. RabbitMQ messages are durable triggers and duplicate delivery is a
normal, supported condition.

The reliability model is:

```text
submission + transactional outbox
        -> publisher confirm
        -> durable RabbitMQ trigger (at least once)
        -> database CAS/lease claim (idempotent)
        -> Runner
        -> result transaction commit
        -> manual ACK
```

No PostgreSQL/RabbitMQ XA transaction is used. The outbox prevents message
loss; database claims and judge tokens prevent duplicates from producing a
second effective business result.

## Submission and outbox transaction

The submit API commits the new `Submission` and one `JUDGE_REQUESTED`
`judge_outbox` event in the same PostgreSQL transaction. The message payload
contains only a stable `eventId`, `submissionId`, schema version, and delivery
attempt. The Worker reloads source code, problem limits, and test cases from
PostgreSQL instead of trusting stale message data.

An HTTP success means that the platform durably accepted the submission. It
does not mean RabbitMQ was reachable at that instant. If RabbitMQ is down, the
submission remains `PENDING` and the outbox relay publishes it after recovery.

The relay claims batches with `FOR UPDATE SKIP LOCKED`, then releases the
database transaction before waiting for RabbitMQ. A claimed row has a bounded
`PUBLISHING` lease and a random publisher token. Only the current token may
mark it `PUBLISHED` or retry it. An expired claim is recoverable after a relay
crash.

Only a positive correlated publisher confirm marks an event `PUBLISHED`.
NACK, returned messages, timeouts, and connection failures return the event to
`PENDING` with exponential backoff and a sanitized failure category. A crash
after the broker accepted the message but before the database update may
publish the same stable `eventId` again; the Worker is designed for this.

Published rows are retained for seven days by default and then removed in
bounded batches. Pending, publishing, and retryable rows are never deleted by
retention.

## Worker ownership and crash recovery

The durable judge queue uses manual acknowledgements and prefetch `1`. A
Worker first claims PostgreSQL state with a conditional update:

```text
PENDING -> JUDGING + judge_token + judge_lease_until
```

An expired `JUDGING` lease can be reclaimed with a new token. A live lease is
not executed by another Worker; that trigger is delayed through the retry
queue. Completion is conditional on both `JUDGING` and the current token, so a
late or recovered Worker cannot overwrite a newer result.

The result transaction commits before RabbitMQ is acknowledged. If the ACK is
lost, redelivery sees the final database verdict, treats it as an idempotent
success, and ACKs without invoking the Runner again. If a Worker dies after
claiming, the message is redelivered and another Worker reclaims it after the
lease expires. The configured lease must remain longer than the platform's
hard upper bound for compile, execution, network, and cleanup time.

Final verdicts (`AC`, `WA`, `CE`, `RE`, `TLE`, `MLE`, `OLE`, and equivalent
student-code outcomes) are normal judge results. They are committed and ACKed;
they are not infrastructure retries.

## Retry and dead-letter topology

All exchanges and queues are durable, messages are persistent, and no judge
queue is exclusive or auto-delete:

```text
oj.judge / oj.judge.queue
        -> oj.judge.retry / oj.judge.retry.queue (TTL)
        -> oj.judge / oj.judge.queue

terminal infrastructure failure
        -> oj.judge.dlx / oj.judge.dlq
```

Retry is broker-delayed; Workers never sleep while holding a delivery. The
default maximum is three execution attempts. Temporary Runner/Docker/HTTP
failures release the database claim and publish a trigger with an incremented
delivery attempt. The last failure publishes a sanitized dead-letter record,
sets the submission to `JUDGE_FAILED`, and ACKs the original message.

The dead-letter record keeps `eventId`, `submissionId`, original routing key,
attempt count, failure category, and timestamp. It never includes student
source, Runner tokens, Docker socket details, or service credentials.

## Operations and observability

Correlate events with these safe log fields: `eventId`, `submissionId`,
`judgeToken`, delivery attempt, Worker instance, and Runner request ID. Logs
cover outbox claim/confirm/retry, message receipt, claim, duplicate ignore,
Runner completion, retry, DLQ, database commit, and ACK boundaries without
printing source code or secrets.

Backend health reports the count of unpublished outbox rows and the relay's
current status/last sanitized failure. Operators should also monitor RabbitMQ
connectivity and the ready/unacknowledged counts of the judge, retry, and DLQ
queues. A rising outbox pending count indicates publisher or RabbitMQ trouble;
a rising DLQ count requires investigation and an explicit operational decision.

Important configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `JUDGE_MAX_RETRIES` | `3` | Total infrastructure execution attempts |
| `JUDGE_RETRY_DELAY_MS` | `5000` | Retry queue TTL |
| `JUDGE_LEASE` | `30m` | Worker ownership lease |
| `JUDGE_PUBLISH_CONFIRM_TIMEOUT` | `5s` | Worker retry/DLQ confirm timeout |
| `OUTBOX_BATCH_SIZE` | `20` | Events claimed per relay poll |
| `OUTBOX_LEASE` | `30s` | Recoverable publisher claim lease |
| `OUTBOX_CONFIRM_TIMEOUT` | `5s` | Outbox RabbitMQ confirm timeout |
| `OUTBOX_INITIAL_RETRY_DELAY` | `1s` | Initial outbox publish backoff |
| `OUTBOX_MAX_RETRY_DELAY` | `1m` | Maximum outbox publish backoff |
| `OUTBOX_RETENTION` | `7d` | Published event retention |

`scripts/test-judge-reliability.sh` is the disposable fault-injection gate. It
tests RabbitMQ outage/recovery, stable duplicate events with three Workers,
lease recovery after killing the owning Worker, result idempotency, temporary
Runner recovery, bounded retries, DLQ, and `JUDGE_FAILED`. It removes only its
named test resources and never prunes Docker globally.
