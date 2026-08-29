package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.dto.SubmitRequest;
import com.oj.dto.SubmissionView;
import com.oj.entity.ProblemEntity;
import com.oj.entity.SubmissionEntity;
import com.oj.entity.UserEntity;
import com.oj.contest.ContentVisibility;
import com.oj.mapper.SubmissionMapper;
import com.oj.mapper.UserMapper;
import com.oj.reliability.JudgeMessage;
import com.oj.reliability.JudgeOutboxRepository;
import com.oj.observability.OperationalMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private static final Duration RATE_LIMIT = Duration.ofSeconds(5);

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemService problemService;
    private final JudgeOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OperationalMetrics metrics;
    private final Map<Integer, LocalDateTime> lastSubmit = new ConcurrentHashMap<>();

    public SubmissionService(SubmissionMapper submissionMapper, UserMapper userMapper,
                             ProblemService problemService, JudgeOutboxRepository outboxRepository,
                             ObjectMapper objectMapper, OperationalMetrics metrics) {
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemService = problemService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public int submit(SubmitRequest request) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        LocalDateTime last = lastSubmit.get(userId);
        if (last != null && Duration.between(last, LocalDateTime.now()).compareTo(RATE_LIMIT) < 0) {
            throw ApiException.tooMany("提交过于频繁，请 5 秒后再试");
        }

        ProblemEntity problem = problemService.getEntityById(request.getProblemId());
        if (!Boolean.TRUE.equals(problem.getVisible())
                || !ContentVisibility.PUBLIC.name().equals(problem.getContentVisibility())) {
            throw ApiException.conflict("该题目已停用，无法继续提交");
        }

        int submissionId = persistSubmission(userId, problem, request.getLanguage(), request.getCode(), null);
        lastSubmit.put(userId, LocalDateTime.now());
        return submissionId;
    }

    @Transactional
    public int submitContest(ProblemEntity problem, String language, String code, long contestProblemId) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        return persistSubmission(userId, problem, language, code, contestProblemId);
    }

    private int persistSubmission(int userId, ProblemEntity problem, String language,
                                  String code, Long contestProblemId) {

        SubmissionEntity submission = new SubmissionEntity();
        submission.setUserId(userId);
        submission.setProblemId(problem.getId());
        submission.setContestProblemId(contestProblemId);
        submission.setLanguage(language);
        submission.setCode(code);
        submission.setVerdict("PENDING");
        submission.setTimeMs(0);
        submission.setMemoryKb(0);
        submission.setPassed(0);
        submission.setTotal(0);
        submission.setJudgeGeneration(0);
        submission.setMessage("排队中");
        submissionMapper.insert(submission);

        try {
            JudgeMessage message = JudgeMessage.initial(submission.getId(), 0);
            String payload = objectMapper.writeValueAsString(message);
            outboxRepository.insert(message.eventId(), submission.getId(), 0, payload);
            metrics.submissionAccepted();
            log.info("Submission accepted submissionId={} problemId={} contestId={} eventId={}",
                    submission.getId(), problem.getId(), contestProblemId, message.eventId());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist judge event", exception);
        }

        return submission.getId();
    }

    public SubmissionView getById(int id) {
        SubmissionEntity submission = submissionMapper.selectById(id);
        if (submission == null) throw ApiException.notFound("提交不存在");
        if (!CurrentUser.isAdmin() && !Objects.equals(submission.getUserId(), CurrentUser.getId())) {
            throw ApiException.forbidden("无权查看该提交");
        }
        return toView(submission, true);
    }

    public List<SubmissionView> listFeed(int page, int pageSize, Integer problemId) {
        QueryWrapper<SubmissionEntity> query = visibleSubmissionQuery(problemId);
        query.orderByDesc("created_at");
        Page<SubmissionEntity> result = submissionMapper.selectPage(new Page<>(page, pageSize), query);
        return result.getRecords().stream().map(submission -> toView(submission, false)).toList();
    }

    public long countFeed(Integer problemId) {
        return submissionMapper.selectCount(visibleSubmissionQuery(problemId));
    }

    public List<SubmissionView> mySubmissions(int userId, int page, int pageSize) {
        QueryWrapper<SubmissionEntity> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("created_at");
        Page<SubmissionEntity> result = submissionMapper.selectPage(new Page<>(page, pageSize), query);
        return result.getRecords().stream().map(submission -> toView(submission, false)).toList();
    }

    public long countMine(int userId) {
        return submissionMapper.selectCount(new QueryWrapper<SubmissionEntity>().eq("user_id", userId));
    }

    private QueryWrapper<SubmissionEntity> visibleSubmissionQuery(Integer problemId) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");
        QueryWrapper<SubmissionEntity> query = new QueryWrapper<>();
        if (!CurrentUser.isAdmin()) query.eq("user_id", userId);
        if (problemId != null) query.eq("problem_id", problemId);
        return query;
    }

    private SubmissionView toView(SubmissionEntity submission, boolean includeCode) {
        SubmissionView view = new SubmissionView();
        view.setId(submission.getId());
        view.setUserId(submission.getUserId());
        view.setProblemId(submission.getProblemId());
        view.setContestProblemId(submission.getContestProblemId());
        view.setLanguage(submission.getLanguage());
        if (includeCode) view.setCode(submission.getCode());
        view.setVerdict(submission.getVerdict());
        view.setTimeMs(submission.getTimeMs());
        view.setMemoryKb(submission.getMemoryKb());
        view.setMessage(submission.getMessage());
        view.setPassed(submission.getPassed());
        view.setTotal(submission.getTotal());
        view.setCreatedAt(submission.getCreatedAt());

        try {
            ProblemEntity problem = problemService.getEntityById(submission.getProblemId());
            view.setProblem(new SubmissionView.ProblemBrief(
                    problem.getId(), problem.getSlug(), problem.getTitle(), problem.getDifficulty()));
        } catch (ApiException ignored) {
            // Historical view remains safe if content was removed concurrently.
        }

        UserEntity user = userMapper.selectById(submission.getUserId());
        if (user != null) view.setUser(new SubmissionView.UserBrief(user.getId(), user.getUsername()));
        return view;
    }
}
