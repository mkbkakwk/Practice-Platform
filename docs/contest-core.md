# Contest core

Stage 6/6.6 adds the contest business boundary without introducing ranking or
a second judging pipeline. PostgreSQL is the state source of truth, and an
injected UTC `Clock` is the time source of truth.

## Data model

- `Contest` owns title, description, owner, `DRAFT|PUBLISHED|CANCELLED`,
  `OPEN|INVITE_ONLY`, and `TIMESTAMPTZ` start/end timestamps.
- `ContestParticipant` links one student to one contest and records who added
  them and when. `(contest_id, user_id)` is unique.
- `ContestProblem` has exactly one type and target: `ALGORITHM` references a
  `Problem`, `OFFICE_CHOICE` references an `OfficeQuestion`, and `OFFICE_DOCX`
  references an `OfficeExercise`. A database `CHECK`, foreign keys, partial
  unique indexes, and deterministic `display_order` prevent ambiguous or
  duplicate links.
- Algorithm `Submission`, DOCX `OfficeDocSubmission`, and choice `OfficeRecord`
  carry an optional immutable `contest_problem_id`. Practice submissions keep
  it `NULL`.

Contest core starts at Flyway V7. V8 adds the two Office contest types, maps
historical `OFFICE` contest rows to `OFFICE_DOCX`, and preserves existing DOCX
reference paths. Historical questions remain `PUBLIC`; existing DOCX exercises
do not silently treat their reference document as a student starter.

## Lifecycle and time

Only `DRAFT`, `PUBLISHED`, and `CANCELLED` are persisted. The API derives:

```text
DRAFT                                  -> DRAFT
PUBLISHED and now < startAt            -> UPCOMING
PUBLISHED and startAt <= now < endAt   -> RUNNING
PUBLISHED and now >= endAt             -> ENDED
CANCELLED                              -> CANCELLED
```

The submission interval is `[startAt, endAt)`. A request at the exact start is
accepted and one at the exact end is rejected. This check occurs next to
submission creation under a locked Contest row. Once a submission and its
context have committed, the Worker does not re-check contest time; judging may
finish after the contest ends.

Draft and upcoming core configuration may be edited. Running, ended, and
cancelled contests are frozen. Only draft contests without submissions may be
physically deleted. Cancellation retains problems, participants, and history.

## Access and participants

Public registration produces the existing `USER` role, which is the Stage 6
student role. Only students may be participants.

- `OPEN`: authenticated students may self-join only while the contest is
  published and upcoming. Concurrent joins are idempotent through the database
  unique constraint.
- `INVITE_ONLY`: self-join is rejected. The owner or an administrator manages
  the roster while the contest is draft or upcoming.

Teachers create and manage only their contests and only content they already
have permission to manage. Administrators may manage all contests and content.
Students cannot call management operations. Student contest lists include
published OPEN contests and their published INVITE_ONLY contests; unrelated
invite-only contests and drafts are hidden.

## Problem visibility

Algorithm Problems, OfficeQuestions, and DOCX OfficeExercises use:

- `PUBLIC`: remains available in the ordinary practice area. Adding it to a
  contest does not hide it, so teachers must not assume it is secret.
- `CONTEST_ONLY`: excluded from ordinary lists and protected on direct detail
  APIs. A student may read it only when they participate in a published contest
  containing it and the contest has started. Participants retain access after
  the end; unrelated students never gain access.

The Contest DTO never exposes algorithm test cases, DOCX reference paths/files,
choice answers/explanations, or user authentication fields. During an upcoming
contest, hidden contest-only problem bodies are not returned. A running choice
submission reports only correct/incorrect and never returns the correct answer
or explanation.

## Submission integration

Contest endpoints derive context from the URL and database relationship:

```text
POST /api/contests/{contestId}/problems/{contestProblemId}/submissions
POST /api/contests/{contestId}/problems/{contestProblemId}/choice-submissions
POST /api/contests/{contestId}/problems/{contestProblemId}/office-submissions
GET  /api/contests/{contestId}/problems/{contestProblemId}/starter
```

The server verifies contest, phase, participant, association, underlying type,
and enabled content. Clients cannot inject context into the ordinary practice
submission endpoints.

Algorithm contests reuse the existing atomic `Submission + judge_outbox`
transaction and therefore continue through RabbitMQ, idempotent Workers, the
Runner, and per-submission Docker sandbox. Choice contests reuse the existing
single/multiple/true-false evaluator while persisting contest context. DOCX
contests reuse Stage 5 file validation, storage lifecycle, canonical comparison,
structured scoring, and sanitized failure handling.

Each usable DOCX exercise has two independent validated files. The starter is
the student-downloadable document to edit; the reference is the server-only
answer used for judging and authorized review. PUBLIC starters are available
from the ordinary exercise endpoint. CONTEST_ONLY starters are available only
through the contest endpoint after start to participants (and remain readable
after end); the reference is never exposed to students.

## Stage 7 extensions

Stage 7 adds SCORE/ICPC scoring, derived standings, server-authorized freeze
views, and generation-safe algorithm rejudge. The full semantics and the
intentional deferral of Office historical rejudge are documented in
[Contest scoring](contest-scoring.md). Virtual participation, teams,
announcements, and post-contest public release remain out of scope.
