package com.oj.service;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.ContestException;
import com.oj.contest.ContestLifecycle;
import com.oj.contest.ContestPhase;
import com.oj.contest.ContestProblemType;
import com.oj.contest.ContestScoringMode;
import com.oj.dto.ContestDtos;
import com.oj.entity.ContestEntity;
import com.oj.entity.ContestProblemEntity;
import com.oj.mapper.ContestMapper;
import com.oj.mapper.ContestProblemMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only, bulk-loaded standings. No standings truth table is persisted. */
@Service
public class ContestStandingService {
    private static final Set<String> TERMINAL = Set.of("AC", "WA", "TLE", "MLE", "OLE", "RE", "CE", "SE");
    private static final Set<String> ICPC_WRONG = Set.of("WA", "TLE", "MLE", "OLE", "RE", "CE", "SE");

    private final ContestMapper contestMapper;
    private final ContestProblemMapper problemMapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ContestStandingService(ContestMapper contestMapper, ContestProblemMapper problemMapper,
                                  JdbcTemplate jdbc, Clock clock) {
        this.contestMapper = contestMapper;
        this.problemMapper = problemMapper;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ContestDtos.Standing standings(int contestId) {
        requireAuthenticated();
        ContestEntity contest = contestMapper.selectById(contestId);
        if (contest == null) throw ContestException.notFound();
        ContestPhase phase = ContestLifecycle.phase(contest, clock);
        boolean manager = canManage(contest);
        if (!manager) {
            if (phase != ContestPhase.RUNNING && phase != ContestPhase.ENDED) {
                throw ContestException.conflict("STANDINGS_NOT_AVAILABLE", "比赛进行中或结束后才可查看排名");
            }
            Long participant = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM "ContestParticipant" WHERE contest_id = ? AND user_id = ?
                    """, Long.class, contestId, CurrentUser.getId());
            if (participant == null || participant == 0) {
                throw ContestException.forbidden("NOT_CONTEST_PARTICIPANT", "不是该比赛参赛者");
            }
        }
        if (phase == ContestPhase.DRAFT || phase == ContestPhase.UPCOMING || phase == ContestPhase.CANCELLED) {
            throw ContestException.conflict("STANDINGS_NOT_AVAILABLE", "当前比赛阶段不可查看排名");
        }
        boolean frozen = !manager && phase == ContestPhase.RUNNING && contest.getFreezeAt() != null
                && !clock.instant().isBefore(contest.getFreezeAt());
        // Every public result is constrained to the real submission window.  An
        // ENDED contest automatically reveals the final [startAt, endAt) view;
        // a frozen RUNNING contest substitutes freezeAt for endAt to keep the
        // post-freeze activity completely out of the response.
        Instant cutoff = phase == ContestPhase.ENDED ? contest.getEndAt()
                : frozen ? contest.getFreezeAt() : null;
        List<ContestProblemEntity> problems = problemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContestProblemEntity>()
                        .eq("contest_id", contestId).orderByAsc("display_order").orderByAsc("id"));
        List<Participant> participants = jdbc.query("""
                SELECT participant.user_id, user_row.username
                FROM "ContestParticipant" participant
                JOIN "User" user_row ON user_row.id = participant.user_id
                WHERE participant.contest_id = ?
                ORDER BY participant.user_id
                """, (rs, row) -> new Participant(rs.getInt(1), rs.getString(2)), contestId);
        Map<Integer, StandingState> states = participants.stream().collect(Collectors.toMap(
                Participant::userId, participant -> new StandingState(participant, problems), (a, b) -> a, LinkedHashMap::new));
        if (!problems.isEmpty()) {
            loadAlgorithm(states, problems, contest.getStartAt(), cutoff);
            loadChoice(states, problems, contest.getStartAt(), cutoff);
            loadDocx(states, problems, contest.getStartAt(), cutoff);
        }
        ContestScoringMode mode = ContestScoringMode.valueOf(
                contest.getScoringMode() == null ? ContestScoringMode.SCORE.name() : contest.getScoringMode());
        List<ContestDtos.StandingEntry> entries = mode == ContestScoringMode.ICPC
                ? icpcEntries(states, problems, contest.getStartAt())
                : scoreEntries(states, problems);
        return new ContestDtos.Standing(contestId, mode.name(), phase.name(), frozen, manager,
                contest.getFreezeAt(), clock.instant(), entries);
    }

    private void loadAlgorithm(Map<Integer, StandingState> states, List<ContestProblemEntity> problems,
                               Instant startAt, Instant cutoff) {
        List<Long> ids = problems.stream().filter(problem -> ContestProblemType.ALGORITHM.name().equals(problem.getProblemType()))
                .map(ContestProblemEntity::getId).toList();
        if (ids.isEmpty()) return;
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String cutoffSql = cutoff == null ? "" : " AND submission.created_at < (? AT TIME ZONE 'UTC')";
        List<Object> args = new ArrayList<>(ids);
        args.add(Timestamp.from(startAt));
        if (cutoff != null) args.add(Timestamp.from(cutoff));
        jdbc.query("""
                SELECT submission.user_id, submission.contest_problem_id, submission.id, submission.created_at,
                       CASE WHEN submission.verdict IN ('PENDING','JUDGING','JUDGE_FAILED')
                            THEN history.verdict ELSE submission.verdict END AS effective_verdict
                FROM "Submission" submission
                LEFT JOIN LATERAL (
                    SELECT verdict FROM algorithm_judge_history
                    WHERE submission_id = submission.id AND judge_generation <= submission.judge_generation
                      AND verdict <> 'JUDGE_FAILED'
                    ORDER BY judge_generation DESC LIMIT 1
                ) history ON submission.verdict IN ('PENDING','JUDGING','JUDGE_FAILED')
                WHERE submission.contest_problem_id IN (""" + placeholders + ")"
                + " AND submission.created_at >= (? AT TIME ZONE 'UTC')" + cutoffSql
                + " ORDER BY submission.user_id, submission.contest_problem_id, submission.created_at, submission.id",
                rs -> {
                    StandingState state = states.get(rs.getInt("user_id"));
                    if (state != null) {
                        String verdict = rs.getString("effective_verdict");
                        if (TERMINAL.contains(verdict)) {
                            state.algorithm(rs.getLong("contest_problem_id")).add(
                                    verdict, toInstant(rs.getTimestamp("created_at")), rs.getInt("id"));
                        }
                    }
                }, args.toArray());
    }

    private void loadChoice(Map<Integer, StandingState> states, List<ContestProblemEntity> problems,
                            Instant startAt, Instant cutoff) {
        List<Long> ids = problems.stream().filter(problem -> ContestProblemType.OFFICE_CHOICE.name().equals(problem.getProblemType()))
                .map(ContestProblemEntity::getId).toList();
        if (ids.isEmpty()) return;
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String cutoffSql = cutoff == null ? "" : " AND office_record.created_at < (? AT TIME ZONE 'UTC')";
        List<Object> args = new ArrayList<>(ids);
        args.add(Timestamp.from(startAt));
        if (cutoff != null) args.add(Timestamp.from(cutoff));
        jdbc.query("SELECT office_record.user_id, office_record.contest_problem_id, office_record.correct FROM \"OfficeRecord\" office_record "
                        + "WHERE office_record.contest_problem_id IN (" + placeholders + ")"
                        + " AND office_record.created_at >= (? AT TIME ZONE 'UTC')" + cutoffSql,
                rs -> {
                    StandingState state = states.get(rs.getInt("user_id"));
                    if (state != null) state.choice(rs.getLong("contest_problem_id")).add(rs.getBoolean("correct"));
                }, args.toArray());
    }

    private void loadDocx(Map<Integer, StandingState> states, List<ContestProblemEntity> problems,
                          Instant startAt, Instant cutoff) {
        List<Long> ids = problems.stream().filter(problem -> ContestProblemType.OFFICE_DOCX.name().equals(problem.getProblemType()))
                .map(ContestProblemEntity::getId).toList();
        if (ids.isEmpty()) return;
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String cutoffSql = cutoff == null ? "" : " AND submission.created_at < (? AT TIME ZONE 'UTC')";
        List<Object> args = new ArrayList<>(ids);
        args.add(Timestamp.from(startAt));
        if (cutoff != null) args.add(Timestamp.from(cutoff));
        jdbc.query("SELECT submission.user_id, submission.contest_problem_id, submission.score, submission.status "
                        + "FROM \"OfficeDocSubmission\" submission WHERE submission.contest_problem_id IN ("
                        + placeholders + ") AND submission.created_at >= (? AT TIME ZONE 'UTC')" + cutoffSql,
                rs -> {
                    StandingState state = states.get(rs.getInt("user_id"));
                    Integer score = rs.getObject("score", Integer.class);
                    if (state != null && score != null && !"FAILED".equals(rs.getString("status"))) {
                        state.docx(rs.getLong("contest_problem_id")).add(score);
                    }
                }, args.toArray());
    }

    private List<ContestDtos.StandingEntry> scoreEntries(Map<Integer, StandingState> states,
                                                           List<ContestProblemEntity> problems) {
        List<Computed> computed = states.values().stream().map(state -> {
            List<ContestDtos.StandingProblem> cells = new ArrayList<>();
            int total = 0;
            for (ContestProblemEntity problem : problems) {
                ProblemState value = state.problem(problem);
                int score = value.score();
                total += score;
                cells.add(new ContestDtos.StandingProblem(problem.getId(), problem.getLabel(), score,
                        value.solved(), value.attempts(), null));
            }
            return new Computed(state.participant, total, 0, 0, cells);
        }).sorted(Comparator.comparingInt(Computed::totalScore).reversed()
                .thenComparing(value -> value.participant.userId())).toList();
        return ranked(computed, false);
    }

    private List<ContestDtos.StandingEntry> icpcEntries(Map<Integer, StandingState> states,
                                                          List<ContestProblemEntity> problems, Instant startAt) {
        List<Computed> computed = states.values().stream().map(state -> {
            List<ContestDtos.StandingProblem> cells = new ArrayList<>();
            int solved = 0;
            int penalty = 0;
            for (ContestProblemEntity problem : problems) {
                AlgorithmState value = state.algorithms.get(problem.getId());
                boolean accepted = value != null && value.acceptedAt != null;
                int problemPenalty = accepted ? elapsedMinutes(startAt, value.acceptedAt) + value.wrongBeforeAc * 20 : 0;
                if (accepted) { solved++; penalty += problemPenalty; }
                cells.add(new ContestDtos.StandingProblem(problem.getId(), problem.getLabel(),
                        accepted ? 100 : 0, accepted, value == null ? 0 : value.attempts,
                        accepted ? problemPenalty : null));
            }
            return new Computed(state.participant, solved, solved, penalty, cells);
        }).sorted(Comparator.comparingInt(Computed::solved).reversed()
                .thenComparingInt(Computed::penaltyMinutes)
                .thenComparing(value -> value.participant.userId())).toList();
        return ranked(computed, true);
    }

    private List<ContestDtos.StandingEntry> ranked(List<Computed> values, boolean icpc) {
        List<ContestDtos.StandingEntry> entries = new ArrayList<>();
        int rank = 0;
        Computed previous = null;
        for (int index = 0; index < values.size(); index++) {
            Computed value = values.get(index);
            if (previous == null || (icpc
                    ? previous.solved != value.solved || previous.penaltyMinutes != value.penaltyMinutes
                    : previous.totalScore != value.totalScore)) rank = index + 1;
            entries.add(new ContestDtos.StandingEntry(rank, value.participant.userId, value.participant.username,
                    value.totalScore, value.solved, value.penaltyMinutes, value.problems));
            previous = value;
        }
        return entries;
    }

    private int elapsedMinutes(Instant start, Instant accepted) {
        return (int) Math.max(0, Duration.between(start, accepted).toMinutes());
    }

    private boolean canManage(ContestEntity contest) {
        return CurrentUser.isAdmin() || (CurrentUser.isTeacher() && Objects.equals(CurrentUser.getId(), contest.getOwnerId()));
    }
    private void requireAuthenticated() { if (CurrentUser.getId() == null) throw ApiException.unauthorized("请先登录"); }
    private Instant toInstant(Timestamp timestamp) { return timestamp.toInstant(); }

    private record Participant(Integer userId, String username) {}
    private record Computed(Participant participant, int totalScore, int solved, int penaltyMinutes,
                            List<ContestDtos.StandingProblem> problems) {}

    private static final class StandingState {
        private final Participant participant;
        private final Map<Long, AlgorithmState> algorithms = new HashMap<>();
        private final Map<Long, ChoiceState> choices = new HashMap<>();
        private final Map<Long, DocxState> docx = new HashMap<>();
        private StandingState(Participant participant, List<ContestProblemEntity> problems) {
            this.participant = participant;
            problems.forEach(problem -> {
                if (ContestProblemType.ALGORITHM.name().equals(problem.getProblemType())) algorithms.put(problem.getId(), new AlgorithmState());
                if (ContestProblemType.OFFICE_CHOICE.name().equals(problem.getProblemType())) choices.put(problem.getId(), new ChoiceState());
                if (ContestProblemType.OFFICE_DOCX.name().equals(problem.getProblemType())) docx.put(problem.getId(), new DocxState());
            });
        }
        AlgorithmState algorithm(long id) { return algorithms.computeIfAbsent(id, ignored -> new AlgorithmState()); }
        ChoiceState choice(long id) { return choices.computeIfAbsent(id, ignored -> new ChoiceState()); }
        DocxState docx(long id) { return docx.computeIfAbsent(id, ignored -> new DocxState()); }
        ProblemState problem(ContestProblemEntity problem) {
            return switch (ContestProblemType.valueOf(problem.getProblemType())) {
                case ALGORITHM -> algorithms.get(problem.getId());
                case OFFICE_CHOICE -> choices.get(problem.getId());
                case OFFICE_DOCX -> docx.get(problem.getId());
            };
        }
    }
    private interface ProblemState { int score(); boolean solved(); int attempts(); }
    private static final class AlgorithmState implements ProblemState {
        private int attempts; private int wrongBeforeAc; private Instant acceptedAt;
        void add(String verdict, Instant at, int id) {
            attempts++;
            if (acceptedAt != null) return;
            if ("AC".equals(verdict)) acceptedAt = at;
            else if (ICPC_WRONG.contains(verdict)) wrongBeforeAc++;
        }
        public int score() { return acceptedAt == null ? 0 : 100; }
        public boolean solved() { return acceptedAt != null; }
        public int attempts() { return attempts; }
    }
    private static final class ChoiceState implements ProblemState {
        private int attempts; private boolean correct;
        void add(boolean value) { attempts++; correct |= value; }
        public int score() { return correct ? 100 : 0; }
        public boolean solved() { return correct; }
        public int attempts() { return attempts; }
    }
    private static final class DocxState implements ProblemState {
        private int attempts; private int highest;
        void add(int score) { attempts++; highest = Math.max(highest, score); }
        public int score() { return highest; }
        public boolean solved() { return highest > 0; }
        public int attempts() { return attempts; }
    }
}
