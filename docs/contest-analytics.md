# Contest analytics

Stage 8 provides owner-Teacher and Admin-only, read-only analytics for one contest.
`GET /api/contests/{id}/analytics` returns compact overview, problem, twelve-bucket
timeline, and distribution data. `GET /api/contests/{id}/analytics/participants`
is paginated and supports a server-side username query.

Analytics is derived from participants, contest problems, and real submission rows.
It has no analytics truth table, cache, queue, or background rebuild. Stage 7 standings
remain the single source for SCORE/ICPC rank, score, solved, penalty, and effective
judge-result semantics. Rejudge generations never add submissions; a rejudge can only
change the effective outcome of its original submission.

Problem success rate is successful participants divided by all registered participants.
Algorithm acceptance rate excludes pending/judging/infrastructure-failed outcomes.
`JUDGE_FAILED` is reported separately and never becomes a student wrong attempt. DOCX
uses each submitter's best non-failed scored submission; averages and medians are among
scored submitters, while perfect-score rate uses all participants. `NEEDS_REVIEW` with a
score is included.

Manager analytics remains live during a freeze. Students cannot call either analytics
endpoint, so it cannot bypass frozen standings. Responses intentionally exclude source
code, choice answers/explanations, DOCX comparison/reference data, storage metadata,
and all authentication fields.

The timeline has twelve equal buckets from contest start to `min(now, endAt)` and uses
original submission time. Before a contest starts it is empty. SCORE distribution uses
0%, 1–20%, 21–40%, 41–60%, 61–80%, 81–99%, and 100%; ICPC distribution groups by
solved count.
