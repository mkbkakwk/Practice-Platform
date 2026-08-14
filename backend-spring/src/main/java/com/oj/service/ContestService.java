package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.*;
import com.oj.dto.*;
import com.oj.entity.*;
import com.oj.judge.LanguageDef;
import com.oj.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContestService {
    private static final Logger log = LoggerFactory.getLogger(ContestService.class);
    private static final int MAX_CONTEST_PROBLEMS = 100;

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper participantMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ProblemMapper problemMapper;
    private final OfficeExerciseMapper exerciseMapper;
    private final SubmissionMapper submissionMapper;
    private final OfficeDocSubmissionMapper officeSubmissionMapper;
    private final UserMapper userMapper;
    private final SubmissionService submissionService;
    private final OfficeDocService officeDocService;
    private final ProblemService problemService;
    private final Clock clock;

    public ContestService(ContestMapper contestMapper,
                          ContestParticipantMapper participantMapper,
                          ContestProblemMapper contestProblemMapper,
                          ProblemMapper problemMapper,
                          OfficeExerciseMapper exerciseMapper,
                          SubmissionMapper submissionMapper,
                          OfficeDocSubmissionMapper officeSubmissionMapper,
                          UserMapper userMapper,
                          SubmissionService submissionService,
                          OfficeDocService officeDocService,
                          ProblemService problemService,
                          Clock clock) {
        this.contestMapper = contestMapper;
        this.participantMapper = participantMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.problemMapper = problemMapper;
        this.exerciseMapper = exerciseMapper;
        this.submissionMapper = submissionMapper;
        this.officeSubmissionMapper = officeSubmissionMapper;
        this.userMapper = userMapper;
        this.submissionService = submissionService;
        this.officeDocService = officeDocService;
        this.problemService = problemService;
        this.clock = clock;
    }

    public Map<String, Object> list(int page, int pageSize) {
        requireAuthenticated();
        QueryWrapper<ContestEntity> query = new QueryWrapper<>();
        if (CurrentUser.isAdmin()) {
            // Administrators may inspect every contest.
        } else if (CurrentUser.isTeacher()) {
            query.eq("owner_id", CurrentUser.getId());
        } else {
            query.eq("status", ContestStatus.PUBLISHED.name())
                    .and(nested -> nested.eq("access_type", ContestAccessType.OPEN.name())
                            .or().inSql("id", "SELECT contest_id FROM \"ContestParticipant\" WHERE user_id = "
                                    + CurrentUser.getId()));
        }
        query.orderByDesc("start_at").orderByDesc("id");
        Page<ContestEntity> result = contestMapper.selectPage(new Page<>(page, pageSize), query);
        Set<Integer> joined = participantContestIds(CurrentUser.getId(), result.getRecords());
        Map<Integer, String> owners = userNames(result.getRecords().stream()
                .map(ContestEntity::getOwnerId).collect(Collectors.toSet()));
        List<ContestDtos.Summary> contests = result.getRecords().stream()
                .map(contest -> summary(contest, joined.contains(contest.getId()), owners.get(contest.getOwnerId())))
                .toList();
        return Map.of("total", result.getTotal(), "page", page, "pageSize", pageSize, "contests", contests);
    }

    public ContestDtos.Detail detail(int contestId) {
        ContestEntity contest = requireVisibleContest(contestId);
        boolean participant = isParticipant(contestId, CurrentUser.getId());
        boolean manager = canManage(contest);
        ContestPhase phase = ContestLifecycle.phase(contest, clock);
        List<ContestDtos.ProblemItem> problems = List.of();
        if (manager || (participant && (phase == ContestPhase.RUNNING || phase == ContestPhase.ENDED))) {
            problems = problemItems(contestId, true);
        } else if (participant && phase == ContestPhase.UPCOMING) {
            problems = problemItems(contestId, false);
        }
        String owner = Optional.ofNullable(userMapper.selectById(contest.getOwnerId()))
                .map(UserEntity::getUsername).orElse(null);
        return new ContestDtos.Detail(summary(contest, participant, owner), problems);
    }

    public ContestEntity create(ContestUpsertRequest request) {
        CurrentUser.requireContentManager();
        validateTimes(request.getStartAt(), request.getEndAt());
        ContestAccessType accessType = parseAccessType(request.getAccessType());
        ContestEntity contest = new ContestEntity();
        apply(contest, request, accessType);
        contest.setOwnerId(CurrentUser.getId());
        contest.setStatus(ContestStatus.DRAFT.name());
        contestMapper.insert(contest);
        log.info("Contest action=create contestId={} ownerId={} phase=DRAFT", contest.getId(), contest.getOwnerId());
        return contest;
    }

    @Transactional
    public ContestEntity update(int contestId, ContestUpsertRequest request) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        requireConfigMutable(contest);
        validateTimes(request.getStartAt(), request.getEndAt());
        if (ContestStatus.PUBLISHED.name().equals(contest.getStatus())
                && !request.getStartAt().isAfter(clock.instant())) {
            throw ContestException.conflict("CONTEST_LOCKED", "已发布比赛的开始时间必须仍在未来");
        }
        apply(contest, request, parseAccessType(request.getAccessType()));
        contest.setUpdatedAt(clock.instant());
        contestMapper.updateById(contest);
        log.info("Contest action=update contestId={} ownerId={} phase={}", contestId,
                contest.getOwnerId(), ContestLifecycle.phase(contest, clock));
        return contest;
    }

    @Transactional
    public ContestEntity publish(int contestId) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        if (ContestStatus.PUBLISHED.name().equals(contest.getStatus())) return contest;
        if (!ContestStatus.DRAFT.name().equals(contest.getStatus())) {
            throw ContestException.conflict("CONTEST_CANCELLED", "已取消比赛不能发布");
        }
        if (!contest.getStartAt().isAfter(clock.instant())) {
            throw ContestException.conflict("INVALID_CONTEST_TIME", "比赛必须在开始前发布");
        }
        List<ContestProblemEntity> problems = contestProblems(contestId);
        if (problems.isEmpty()) {
            throw ContestException.conflict("CONTEST_EMPTY", "比赛至少需要一道题目");
        }
        validatePublishProblems(problems);
        contest.setStatus(ContestStatus.PUBLISHED.name());
        contest.setUpdatedAt(clock.instant());
        contestMapper.updateById(contest);
        log.info("Contest action=publish contestId={} ownerId={} phase=UPCOMING", contestId, contest.getOwnerId());
        return contest;
    }

    @Transactional
    public ContestEntity cancel(int contestId) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        if (ContestStatus.CANCELLED.name().equals(contest.getStatus())) return contest;
        if (ContestLifecycle.phase(contest, clock) == ContestPhase.ENDED) {
            throw ContestException.conflict("CONTEST_ENDED", "已结束比赛不能取消");
        }
        contest.setStatus(ContestStatus.CANCELLED.name());
        contest.setUpdatedAt(clock.instant());
        contestMapper.updateById(contest);
        log.info("Contest action=cancel contestId={} ownerId={} phase=CANCELLED", contestId, contest.getOwnerId());
        return contest;
    }

    @Transactional
    public Map<String, Object> delete(int contestId) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        if (!ContestStatus.DRAFT.name().equals(contest.getStatus())) {
            throw ContestException.conflict("CONTEST_LOCKED", "只有草稿比赛可以删除");
        }
        List<Long> contestProblemIds = contestProblems(contestId).stream().map(ContestProblemEntity::getId).toList();
        if (!contestProblemIds.isEmpty()
                && (submissionMapper.selectCount(new QueryWrapper<SubmissionEntity>()
                        .in("contest_problem_id", contestProblemIds)) > 0
                || officeSubmissionMapper.selectCount(new QueryWrapper<OfficeDocSubmissionEntity>()
                        .in("contest_problem_id", contestProblemIds)) > 0)) {
            throw ContestException.conflict("CONTEST_HAS_SUBMISSIONS", "已有比赛提交，不能删除");
        }
        contestMapper.deleteById(contestId);
        log.info("Contest action=delete contestId={} ownerId={}", contestId, contest.getOwnerId());
        return Map.of("deleted", true);
    }

    @Transactional
    public ContestDtos.Participant join(int contestId) {
        requireStudent(CurrentUser.getId());
        ContestEntity contest = lockedContest(contestId);
        if (!ContestStatus.PUBLISHED.name().equals(contest.getStatus())) {
            throw ContestException.conflict("CONTEST_NOT_PUBLISHED", "比赛尚未发布");
        }
        if (!ContestAccessType.OPEN.name().equals(contest.getAccessType())) {
            throw ContestException.forbidden("INVITE_ONLY", "邀请制比赛不能自行加入");
        }
        if (ContestLifecycle.phase(contest, clock) != ContestPhase.UPCOMING) {
            throw ContestException.conflict("CONTEST_LOCKED", "只能在公开比赛开始前加入");
        }
        participantMapper.insertIfAbsent(contestId, CurrentUser.getId(), CurrentUser.getId());
        ContestParticipantEntity participant = findParticipant(contestId, CurrentUser.getId());
        log.info("Contest action=join contestId={} userId={} phase=UPCOMING", contestId, CurrentUser.getId());
        return participantDto(participant, CurrentUser.getUsername());
    }

    @Transactional
    public ContestDtos.Participant addParticipant(int contestId, int userId) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        requireConfigMutable(contest);
        UserEntity user = requireStudent(userId);
        participantMapper.insertIfAbsent(contestId, userId, CurrentUser.getId());
        ContestParticipantEntity participant = findParticipant(contestId, userId);
        log.info("Contest action=participant-add contestId={} userId={} ownerId={} phase={}",
                contestId, userId, contest.getOwnerId(), ContestLifecycle.phase(contest, clock));
        return participantDto(participant, user.getUsername());
    }

    @Transactional
    public Map<String, Object> removeParticipant(int contestId, int userId) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        requireConfigMutable(contest);
        int deleted = participantMapper.delete(new QueryWrapper<ContestParticipantEntity>()
                .eq("contest_id", contestId).eq("user_id", userId));
        return Map.of("removed", deleted > 0);
    }

    public Map<String, Object> participants(int contestId, int page, int pageSize) {
        ContestEntity contest = requireContest(contestId);
        requireManage(contest);
        QueryWrapper<ContestParticipantEntity> query = new QueryWrapper<ContestParticipantEntity>()
                .eq("contest_id", contestId).orderByAsc("joined_at").orderByAsc("id");
        Page<ContestParticipantEntity> result = participantMapper.selectPage(new Page<>(page, pageSize), query);
        Map<Integer, String> names = userNames(result.getRecords().stream()
                .map(ContestParticipantEntity::getUserId).collect(Collectors.toSet()));
        List<ContestDtos.Participant> participants = result.getRecords().stream()
                .map(value -> participantDto(value, names.get(value.getUserId()))).toList();
        return Map.of("total", result.getTotal(), "page", page, "pageSize", pageSize,
                "participants", participants);
    }

    @Transactional
    public ContestDtos.ProblemItem addProblem(int contestId, ContestProblemRequest request) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        requireConfigMutable(contest);
        if (contestProblemMapper.selectCount(new QueryWrapper<ContestProblemEntity>()
                .eq("contest_id", contestId)) >= MAX_CONTEST_PROBLEMS) {
            throw ContestException.conflict("CONTEST_PROBLEM_LIMIT", "比赛题目数量超过上限");
        }
        ContestProblemType type = ContestProblemType.valueOf(request.getProblemType());
        ContestProblemEntity item = new ContestProblemEntity();
        item.setContestId(contestId);
        item.setProblemType(type.name());
        item.setDisplayOrder(nextOrder(contestId));
        item.setLabel(normalizeLabel(request.getLabel(), item.getDisplayOrder()));
        if (type == ContestProblemType.ALGORITHM) {
            ProblemEntity problem = requireUsableAlgorithmProblem(request.getProblemId());
            item.setAlgorithmProblemId(problem.getId());
        } else {
            OfficeExerciseEntity exercise = requireUsableOfficeExercise(request.getProblemId());
            item.setOfficeExerciseId(exercise.getId());
        }
        try {
            contestProblemMapper.insert(item);
        } catch (DataIntegrityViolationException exception) {
            throw ContestException.conflict("DUPLICATE_CONTEST_PROBLEM", "该题目已在比赛中");
        }
        log.info("Contest action=problem-add contestId={} contestProblemId={} ownerId={} phase={}",
                contestId, item.getId(), contest.getOwnerId(), ContestLifecycle.phase(contest, clock));
        return problemItem(item, true, Map.of(), Map.of());
    }

    @Transactional
    public Map<String, Object> removeProblem(int contestId, long contestProblemId) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        requireConfigMutable(contest);
        ContestProblemEntity item = requireContestProblem(contestId, contestProblemId);
        contestProblemMapper.deleteById(item.getId());
        normalizeOrders(contestId);
        return Map.of("removed", true);
    }

    @Transactional
    public List<ContestDtos.ProblemItem> reorderProblems(int contestId, List<Long> ids) {
        ContestEntity contest = lockedContest(contestId);
        requireManage(contest);
        requireConfigMutable(contest);
        List<ContestProblemEntity> current = contestProblems(contestId);
        Set<Long> expected = current.stream().map(ContestProblemEntity::getId).collect(Collectors.toSet());
        if (ids.size() != expected.size() || ids.size() != new HashSet<>(ids).size()
                || !expected.equals(new HashSet<>(ids))) {
            throw ApiException.badRequest("题目顺序必须包含当前比赛的全部题目且不能重复");
        }
        Map<Long, ContestProblemEntity> byId = current.stream()
                .collect(Collectors.toMap(ContestProblemEntity::getId, Function.identity()));
        for (int index = 0; index < ids.size(); index++) {
            ContestProblemEntity item = byId.get(ids.get(index));
            item.setDisplayOrder(1_000_000 + index);
            contestProblemMapper.updateById(item);
        }
        for (int index = 0; index < ids.size(); index++) {
            ContestProblemEntity item = byId.get(ids.get(index));
            item.setDisplayOrder(index + 1);
            item.setLabel(normalizeLabel(item.getLabel(), index + 1));
            contestProblemMapper.updateById(item);
        }
        return problemItems(contestId, true);
    }

    @Transactional
    public int submitAlgorithm(int contestId, long contestProblemId,
                               ContestAlgorithmSubmitRequest request) {
        if (!LanguageDef.isSupported(request.getLanguage())) {
            throw ApiException.badRequest("不支持的语言: " + request.getLanguage());
        }
        ContestProblemEntity item = validateSubmissionContext(contestId, contestProblemId,
                ContestProblemType.ALGORITHM);
        ProblemEntity problem = problemMapper.selectById(item.getAlgorithmProblemId());
        if (problem == null || !Boolean.TRUE.equals(problem.getVisible())) {
            throw ContestException.conflict("CONTEST_PROBLEM_NOT_VISIBLE", "比赛题目当前不可用");
        }
        int submissionId = submissionService.submitContest(
                problem, request.getLanguage(), request.getCode(), contestProblemId);
        log.info("Contest action=algorithm-submit contestId={} contestProblemId={} submissionId={} userId={} phase=RUNNING",
                contestId, contestProblemId, submissionId, CurrentUser.getId());
        return submissionId;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public OfficeSubmissionDtos.StudentSubmission submitOffice(
            int contestId, long contestProblemId, MultipartFile file) {
        ContestProblemEntity item = validateSubmissionContext(contestId, contestProblemId,
                ContestProblemType.OFFICE);
        OfficeExerciseEntity exercise = exerciseMapper.selectById(item.getOfficeExerciseId());
        if (exercise == null || !Boolean.TRUE.equals(exercise.getVisible())) {
            throw ContestException.conflict("CONTEST_PROBLEM_NOT_VISIBLE", "比赛题目当前不可用");
        }
        OfficeSubmissionDtos.StudentSubmission submission =
                officeDocService.submitContestDoc(exercise, file, contestProblemId);
        log.info("Contest action=office-submit contestId={} contestProblemId={} submissionId={} userId={} phase=RUNNING",
                contestId, contestProblemId, submission.id(), CurrentUser.getId());
        return submission;
    }

    private ContestProblemEntity validateSubmissionContext(int contestId, long contestProblemId,
                                                           ContestProblemType expectedType) {
        requireAuthenticated();
        ContestEntity contest = lockedContest(contestId);
        Instant now = clock.instant();
        if (ContestStatus.CANCELLED.name().equals(contest.getStatus())) {
            throw ContestException.conflict("CONTEST_CANCELLED", "比赛已取消");
        }
        if (!ContestStatus.PUBLISHED.name().equals(contest.getStatus())) {
            throw ContestException.conflict("CONTEST_NOT_PUBLISHED", "比赛尚未发布");
        }
        if (now.isBefore(contest.getStartAt())) {
            throw ContestException.conflict("CONTEST_NOT_STARTED", "比赛尚未开始");
        }
        if (!now.isBefore(contest.getEndAt())) {
            throw ContestException.conflict("CONTEST_ENDED", "比赛已结束");
        }
        if (!isParticipant(contestId, CurrentUser.getId())) {
            throw ContestException.forbidden("NOT_CONTEST_PARTICIPANT", "不是该比赛参赛者");
        }
        ContestProblemEntity item = requireContestProblem(contestId, contestProblemId);
        if (!expectedType.name().equals(item.getProblemType())) {
            throw ContestException.conflict("PROBLEM_NOT_IN_CONTEST", "比赛题型与提交入口不匹配");
        }
        return item;
    }

    private ContestEntity requireVisibleContest(int contestId) {
        ContestEntity contest = requireContest(contestId);
        if (canManage(contest)) return contest;
        if (!ContestStatus.PUBLISHED.name().equals(contest.getStatus())) throw ContestException.notFound();
        boolean participant = isParticipant(contestId, CurrentUser.getId());
        if (ContestAccessType.INVITE_ONLY.name().equals(contest.getAccessType()) && !participant) {
            throw ContestException.notFound();
        }
        return contest;
    }

    private ContestEntity requireContest(int contestId) {
        ContestEntity contest = contestMapper.selectById(contestId);
        if (contest == null) throw ContestException.notFound();
        return contest;
    }

    private ContestEntity lockedContest(int contestId) {
        ContestEntity contest = contestMapper.selectByIdForUpdate(contestId);
        if (contest == null) throw ContestException.notFound();
        return contest;
    }

    private ContestProblemEntity requireContestProblem(int contestId, long contestProblemId) {
        ContestProblemEntity item = contestProblemMapper.selectById(contestProblemId);
        if (item == null || !Objects.equals(item.getContestId(), contestId)) {
            throw ContestException.conflict("PROBLEM_NOT_IN_CONTEST", "题目不属于该比赛");
        }
        return item;
    }

    private void requireManage(ContestEntity contest) {
        CurrentUser.requireContentManager();
        if (!canManage(contest)) throw ContestException.forbidden("CONTEST_FORBIDDEN", "无权管理该比赛");
    }

    private boolean canManage(ContestEntity contest) {
        return CurrentUser.isAdmin()
                || (CurrentUser.isTeacher() && Objects.equals(CurrentUser.getId(), contest.getOwnerId()));
    }

    private void requireConfigMutable(ContestEntity contest) {
        ContestPhase phase = ContestLifecycle.phase(contest, clock);
        if (phase != ContestPhase.DRAFT && phase != ContestPhase.UPCOMING) {
            throw ContestException.conflict("CONTEST_LOCKED", "比赛已开始，核心配置已冻结");
        }
    }

    private UserEntity requireStudent(Integer userId) {
        if (userId == null) throw ApiException.unauthorized("请先登录");
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw ApiException.notFound("用户不存在");
        if (!"USER".equals(user.getRole())) {
            throw ContestException.conflict("PARTICIPANT_ROLE_INVALID", "只有学生账号可以成为参赛者");
        }
        return user;
    }

    private void requireAuthenticated() {
        if (CurrentUser.getId() == null) throw ApiException.unauthorized("请先登录");
    }

    private ContestParticipantEntity findParticipant(int contestId, int userId) {
        ContestParticipantEntity participant = participantMapper.selectOne(
                new QueryWrapper<ContestParticipantEntity>()
                        .eq("contest_id", contestId).eq("user_id", userId));
        if (participant == null) throw new IllegalStateException("Participant insert did not persist");
        return participant;
    }

    private boolean isParticipant(int contestId, Integer userId) {
        return userId != null && participantMapper.selectCount(
                new QueryWrapper<ContestParticipantEntity>()
                        .eq("contest_id", contestId).eq("user_id", userId)) > 0;
    }

    private Set<Integer> participantContestIds(Integer userId, List<ContestEntity> contests) {
        if (userId == null || contests.isEmpty()) return Set.of();
        Set<Integer> contestIds = contests.stream().map(ContestEntity::getId).collect(Collectors.toSet());
        return participantMapper.selectList(new QueryWrapper<ContestParticipantEntity>()
                        .eq("user_id", userId).in("contest_id", contestIds).select("contest_id"))
                .stream().map(ContestParticipantEntity::getContestId).collect(Collectors.toSet());
    }

    private ContestDtos.Summary summary(ContestEntity contest, boolean participant, String ownerUsername) {
        return new ContestDtos.Summary(contest.getId(), contest.getTitle(), contest.getDescription(),
                contest.getStatus(), ContestLifecycle.phase(contest, clock).name(), contest.getAccessType(),
                contest.getOwnerId(), ownerUsername, contest.getStartAt(), contest.getEndAt(), participant,
                contest.getCreatedAt(), contest.getUpdatedAt());
    }

    private ContestDtos.Participant participantDto(ContestParticipantEntity participant, String username) {
        return new ContestDtos.Participant(participant.getId(), participant.getUserId(), username,
                participant.getAddedBy(), participant.getJoinedAt());
    }

    private List<ContestProblemEntity> contestProblems(int contestId) {
        return contestProblemMapper.selectList(new QueryWrapper<ContestProblemEntity>()
                .eq("contest_id", contestId).orderByAsc("display_order").orderByAsc("id"));
    }

    private List<ContestDtos.ProblemItem> problemItems(int contestId, boolean includeContestOnly) {
        List<ContestProblemEntity> items = contestProblems(contestId);
        Set<Integer> algorithmIds = items.stream().map(ContestProblemEntity::getAlgorithmProblemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> officeIds = items.stream().map(ContestProblemEntity::getOfficeExerciseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, ProblemEntity> algorithms = algorithmIds.isEmpty() ? Map.of() : problemMapper.selectBatchIds(algorithmIds)
                .stream().collect(Collectors.toMap(ProblemEntity::getId, Function.identity()));
        Map<Integer, OfficeExerciseEntity> offices = officeIds.isEmpty() ? Map.of() : exerciseMapper.selectBatchIds(officeIds)
                .stream().collect(Collectors.toMap(OfficeExerciseEntity::getId, Function.identity()));
        return items.stream()
                .filter(item -> includeContestOnly || isPublic(item, algorithms, offices))
                .map(item -> problemItem(item, includeContestOnly, algorithms, offices)).toList();
    }

    private boolean isPublic(ContestProblemEntity item, Map<Integer, ProblemEntity> algorithms,
                             Map<Integer, OfficeExerciseEntity> offices) {
        if (ContestProblemType.ALGORITHM.name().equals(item.getProblemType())) {
            ProblemEntity problem = algorithms.get(item.getAlgorithmProblemId());
            return problem != null && ContentVisibility.PUBLIC.name().equals(problem.getContentVisibility());
        }
        OfficeExerciseEntity exercise = offices.get(item.getOfficeExerciseId());
        return exercise != null && ContentVisibility.PUBLIC.name().equals(exercise.getContentVisibility());
    }

    private ContestDtos.ProblemItem problemItem(ContestProblemEntity item, boolean includeContent,
                                                Map<Integer, ProblemEntity> algorithms,
                                                Map<Integer, OfficeExerciseEntity> offices) {
        if (ContestProblemType.ALGORITHM.name().equals(item.getProblemType())) {
            ProblemEntity problem = algorithms.get(item.getAlgorithmProblemId());
            if (problem == null) problem = problemMapper.selectById(item.getAlgorithmProblemId());
            Object content = null;
            if (includeContent && problem != null) {
                ProblemDetail detail = problemService.getBySlug(problem.getSlug());
                Map<String, Object> safe = new LinkedHashMap<>();
                safe.put("id", detail.getId());
                safe.put("slug", detail.getSlug());
                safe.put("title", detail.getTitle());
                safe.put("description", detail.getDescription());
                safe.put("inputFmt", detail.getInputFmt());
                safe.put("outputFmt", detail.getOutputFmt());
                safe.put("difficulty", detail.getDifficulty());
                safe.put("tags", detail.getTags());
                safe.put("timeLimit", detail.getTimeLimit());
                safe.put("memoryLimit", detail.getMemoryLimit());
                safe.put("samples", detail.getSamples());
                content = safe;
            }
            return new ContestDtos.ProblemItem(item.getId(), item.getProblemType(), item.getAlgorithmProblemId(),
                    item.getDisplayOrder(), item.getLabel(), problem == null ? null : problem.getTitle(),
                    problem == null ? null : problem.getDifficulty(), problem == null ? null : problem.getSlug(), content);
        }
        OfficeExerciseEntity exercise = offices.get(item.getOfficeExerciseId());
        if (exercise == null) exercise = exerciseMapper.selectById(item.getOfficeExerciseId());
        Object content = null;
        if (includeContent && exercise != null) {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("id", exercise.getId());
            safe.put("title", exercise.getTitle());
            safe.put("description", exercise.getDescription());
            safe.put("difficulty", exercise.getDifficulty());
            safe.put("hasReference", exercise.getTeacherDocPath() != null && !exercise.getTeacherDocPath().isBlank());
            content = safe;
        }
        return new ContestDtos.ProblemItem(item.getId(), item.getProblemType(), item.getOfficeExerciseId(),
                item.getDisplayOrder(), item.getLabel(), exercise == null ? null : exercise.getTitle(),
                exercise == null ? null : exercise.getDifficulty(), null, content);
    }

    private ProblemEntity requireUsableAlgorithmProblem(int problemId) {
        ProblemEntity problem = problemMapper.selectById(problemId);
        if (problem == null || !Boolean.TRUE.equals(problem.getVisible())) {
            throw ApiException.notFound("算法题不存在或已停用");
        }
        if (!CurrentUser.canManage(problem.getCreatedBy())) {
            throw ContestException.forbidden("CONTEST_PROBLEM_FORBIDDEN", "无权将该算法题加入比赛");
        }
        return problem;
    }

    private OfficeExerciseEntity requireUsableOfficeExercise(int exerciseId) {
        OfficeExerciseEntity exercise = exerciseMapper.selectById(exerciseId);
        if (exercise == null || !Boolean.TRUE.equals(exercise.getVisible())) {
            throw ApiException.notFound("DOCX 练习不存在或已停用");
        }
        if (!CurrentUser.canManage(exercise.getCreatedBy())) {
            throw ContestException.forbidden("CONTEST_PROBLEM_FORBIDDEN", "无权将该 DOCX 练习加入比赛");
        }
        return exercise;
    }

    private void validatePublishProblems(List<ContestProblemEntity> problems) {
        for (ContestProblemEntity item : problems) {
            if (ContestProblemType.ALGORITHM.name().equals(item.getProblemType())) {
                ProblemEntity problem = problemMapper.selectById(item.getAlgorithmProblemId());
                if (problem == null || !Boolean.TRUE.equals(problem.getVisible())) {
                    throw ContestException.conflict("CONTEST_PROBLEM_NOT_VISIBLE", "比赛包含不可用算法题");
                }
            } else {
                OfficeExerciseEntity exercise = exerciseMapper.selectById(item.getOfficeExerciseId());
                if (exercise == null || !Boolean.TRUE.equals(exercise.getVisible())
                        || exercise.getTeacherDocPath() == null || exercise.getTeacherDocPath().isBlank()) {
                    throw ContestException.conflict("CONTEST_PROBLEM_NOT_READY", "DOCX 比赛题缺少有效参考文档");
                }
                officeDocService.getTeacherDocFile(exercise.getId());
            }
        }
    }

    private int nextOrder(int contestId) {
        return contestProblems(contestId).stream().mapToInt(ContestProblemEntity::getDisplayOrder).max().orElse(0) + 1;
    }

    private void normalizeOrders(int contestId) {
        List<ContestProblemEntity> items = contestProblems(contestId);
        for (int index = 0; index < items.size(); index++) {
            ContestProblemEntity item = items.get(index);
            if (item.getDisplayOrder() != index + 1) {
                item.setDisplayOrder(index + 1);
                contestProblemMapper.updateById(item);
            }
        }
    }

    private String normalizeLabel(String label, int order) {
        if (label != null && !label.isBlank()) return label.trim();
        if (order <= 26) return String.valueOf((char) ('A' + order - 1));
        return Integer.toString(order);
    }

    private void validateTimes(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw ContestException.conflict("INVALID_CONTEST_TIME", "比赛开始时间必须早于结束时间");
        }
    }

    private ContestAccessType parseAccessType(String accessType) {
        try {
            return ContestAccessType.valueOf(accessType);
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("比赛访问模式无效");
        }
    }

    private void apply(ContestEntity contest, ContestUpsertRequest request, ContestAccessType accessType) {
        contest.setTitle(request.getTitle().trim());
        contest.setDescription(request.getDescription() == null ? "" : request.getDescription());
        contest.setStartAt(request.getStartAt());
        contest.setEndAt(request.getEndAt());
        contest.setAccessType(accessType.name());
    }

    private Map<Integer, String> userNames(Set<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
    }
}
