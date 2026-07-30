package com.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.config.RabbitConfig;
import com.oj.dto.SubmitRequest;
import com.oj.dto.SubmissionView;
import com.oj.entity.ProblemEntity;
import com.oj.entity.SubmissionEntity;
import com.oj.entity.UserEntity;
import com.oj.mapper.SubmissionMapper;
import com.oj.mapper.UserMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubmissionService {

    private static final Duration RATE_LIMIT = Duration.ofSeconds(5);

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemService problemService;
    private final RabbitTemplate rabbitTemplate;
    private final Map<Integer, LocalDateTime> lastSubmit = new ConcurrentHashMap<>();

    public SubmissionService(SubmissionMapper submissionMapper, UserMapper userMapper,
                             ProblemService problemService, RabbitTemplate rabbitTemplate) {
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemService = problemService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public int submit(SubmitRequest request) {
        Integer userId = CurrentUser.getId();
        if (userId == null) throw ApiException.unauthorized("请先登录");

        LocalDateTime last = lastSubmit.get(userId);
        if (last != null && Duration.between(last, LocalDateTime.now()).compareTo(RATE_LIMIT) < 0) {
            throw ApiException.tooMany("提交过于频繁，请 5 秒后再试");
        }

        ProblemEntity problem = problemService.getEntityById(request.getProblemId());
        if (!Boolean.TRUE.equals(problem.getVisible())) {
            throw ApiException.conflict("该题目已停用，无法继续提交");
        }

        SubmissionEntity submission = new SubmissionEntity();
        submission.setUserId(userId);
        submission.setProblemId(problem.getId());
        submission.setLanguage(request.getLanguage());
        submission.setCode(request.getCode());
        submission.setVerdict("PENDING");
        submission.setTimeMs(0);
        submission.setMemoryKb(0);
        submission.setPassed(0);
        submission.setTotal(0);
        submission.setMessage("排队中");
        submissionMapper.insert(submission);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("submissionId", submission.getId());
            payload.put("language", submission.getLanguage());
            payload.put("code", submission.getCode());
            payload.put("timeLimitMs", problem.getTimeLimit());
            payload.put("memoryLimitKb", problem.getMemoryLimit() * 1024);
            payload.put("testCasesJson", problem.getTestCases());
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, payload);
        } catch (Exception exception) {
            submission.setVerdict("SE");
            submission.setMessage("评测服务暂不可用: " + exception.getMessage());
            submissionMapper.updateById(submission);
        }

        lastSubmit.put(userId, LocalDateTime.now());
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
