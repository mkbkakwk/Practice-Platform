# Contest scoring, standings, freeze, and rejudge

Stage 7 adds derived contest standings without introducing a persisted ranking
truth table. PostgreSQL submissions remain authoritative and all phase checks
use the server-side injected `Clock`.

## Scoring modes

`Contest.scoring_mode` is `SCORE` or `ICPC`; historical contests migrate to
`SCORE`. It can be changed only while a contest is a draft and has no contest
submissions. An ICPC contest accepts only algorithm contest problems; the
backend rejects Office Choice and DOCX additions even if a client bypasses the
management UI.

### SCORE

Every contest problem is worth 100 points.

- Algorithm: `AC` is 100 and ordinary student terminal failures (`WA`, `TLE`,
  `MLE`, `OLE`, `RE`, `CE`, `SE`) are 0. `PENDING`, `JUDGING`, and
  `JUDGE_FAILED` are not an effective new result.
- Office Choice: a correct submission is 100; an incorrect one is 0.
- Office DOCX: the existing canonical/effective document score is used.

Each problem takes the highest effective score across submissions. Participants
with no submissions remain in the standings with score 0. Total score orders
descending; equal totals use competition rank, with user ID only as a stable
display order.

### ICPC

ICPC is algorithm-only. A problem is solved on its first effective `AC`.
Before that AC, only `WA`, `TLE`, `MLE`, `OLE`, `RE`, `CE`, and `SE` add a wrong
attempt. `JUDGE_FAILED` and non-terminal states never penalize students.

Per solved problem penalty is whole minutes from contest start to the first AC
plus 20 minutes per prior wrong attempt. Standings order by solved count
descending, then penalty ascending; equal pairs use competition rank and user
ID only makes output deterministic.

## Freeze

`freeze_at` is optional and must satisfy `start_at < freeze_at < end_at`. It is
draft-only configuration. During a running contest at or after `freeze_at`, a
non-manager standings response derives only from submissions in
`[start_at, freeze_at)`. The exact freeze timestamp is post-freeze.

Contest owner Teachers and Administrators receive a server-authorized live view;
there is no client `live=true` switch. An ended contest reveals standings from
the full `[start_at, end_at)` submission window. Freeze never hides a student's
own submission result.

## Algorithm rejudge

Only contest owner Teachers and Administrators can queue a rejudge, and only in
RUNNING or ENDED contests. Supported scopes are one algorithm submission, one
algorithm contest problem, or every algorithm submission in a contest.

Every selected submission increments `judge_generation`; the generation is part
of the deterministic judge event identity and the transactional outbox unique
key. Workers default missing legacy message generations to zero and use
generation-aware compare-and-set updates. A delayed old completion therefore
cannot overwrite a newer generation.

`algorithm_judge_history` records one result per `(submission_id,
judge_generation)`. While a rejudge is pending, judging, or infrastructure
failed, standings retain the newest previous non-infrastructure terminal history
record instead of replacing a student's valid score with a temporary zero.
`rejudge_batch` and `rejudge_batch_item` retain the manager-visible audit and
progress model.

Office Choice and Office DOCX automatic historical rejudge are intentionally
unsupported in Stage 7. Correct historical rejudge requires immutable,
versioned answer-key/reference inputs; current Office authoring permits
replacement, so silently judging old submissions against the current document
would be incorrect audit semantics. The backend returns a conflict rather than
performing a no-op.
