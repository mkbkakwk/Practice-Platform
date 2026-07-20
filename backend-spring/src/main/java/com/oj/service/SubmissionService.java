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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemService problemService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple per-user rate limit: 1 submission per 5 seconds.
    private final Map<Integer, LocalDateTime> lastSubmit = new ConcurrentHashMap<>();
    private static final Duration RATE_LIMIT = Duration.ofSeconds(5);

    public SubmissionService(SubmissionMapper submissionMapper, UserMapper userMapper,
                             ProblemService problemService, RabbitTemplate rabbitTemplate) {
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemService = problemService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Persist a PENDING submission then enqueue it for async judging.
     * Returns the submission id so the frontend can poll for the result.
     */
    public int submit(SubmitRequest req) {
        int userId = CurrentUser.getId();

        // Rate limit
        LocalDateTime last = lastSubmit.get(userId);
        if (last != null && Duration.between(last, LocalDateTime.now()).compareTo(RATE_LIMIT) < 0) {
            throw ApiException.tooMany("提交过于频繁，请 5 秒后再试");
        }

        // Validate problem
        ProblemEntity problem = problemService.getEntityById(req.getProblemId());

        SubmissionEntity s = new SubmissionEntity();
        s.setUserId(userId);
        s.setProblemId(problem.getId());
        s.setLanguage(req.getLanguage());
        s.setCode(req.getCode());
        s.setVerdict("PENDING");
        s.setTimeMs(0);
        s.setMemoryKb(0);
        s.setPassed(0);
        s.setTotal(0);
        s.setMessage("排队中");
        submissionMapper.insert(s);

        // Enqueue to RabbitMQ
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("submissionId", s.getId());
            payload.put("language", s.getLanguage());
            payload.put("code", s.getCode());
            payload.put("timeLimitMs", problem.getTimeLimit());
            payload.put("memoryLimitKb", problem.getMemoryLimit() * 1024);
            payload.put("testCasesJson", problem.getTestCases());
            // convertAndSend uses the Jackson2JsonMessageConverter to serialize
            // the Map as a JSON object that the worker can deserialize back to Map.
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, payload);
        } catch (Exception e) {
            // If MQ fails, mark as SE so the frontend doesn't poll forever.
            s.setVerdict("SE");
            s.setMessage("评测服务暂不可用: " + e.getMessage());
            submissionMapper.updateById(s);
        }

        lastSubmit.put(userId, LocalDateTime.now());
        return s.getId();
    }

    public SubmissionView getById(int id) {
        SubmissionEntity s = submissionMapper.selectById(id);
        if (s == null) throw ApiException.notFound("提交不存在");
        Integer me = CurrentUser.getId();
        if (s.getUserId() != me && !CurrentUser.isAdmin()) {
            throw ApiException.forbidden("无权查看该提交");
        }
        return toView(s, true);
    }

    public List<SubmissionView> listFeed(int page, int pageSize, Integer problemId) {
        QueryWrapper<SubmissionEntity> qw = new QueryWrapper<>();
        if (problemId != null) qw.eq("problem_id", problemId);
        qw.orderByDesc("created_at");
        Page<SubmissionEntity> p = submissionMapper.selectPage(new Page<>(page, pageSize), qw);
        return p.getRecords().stream().map(s -> toView(s, false)).toList();
    }

    public List<SubmissionView> mySubmissions(int userId, int page, int pageSize) {
        QueryWrapper<SubmissionEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.orderByDesc("created_at");
        Page<SubmissionEntity> p = submissionMapper.selectPage(new Page<>(page, pageSize), qw);
        return p.getRecords().stream().map(s -> toView(s, false)).toList();
    }

    public long countFeed(Integer problemId) {
        QueryWrapper<SubmissionEntity> qw = new QueryWrapper<>();
        if (problemId != null) qw.eq("problem_id", problemId);
        return submissionMapper.selectCount(qw);
    }

    public long countMine(int userId) {
        return submissionMapper.selectCount(new QueryWrapper<SubmissionEntity>().eq("user_id", userId));
    }

    private SubmissionView toView(SubmissionEntity s, boolean includeCode) {
        SubmissionView v = new SubmissionView();
        v.setId(s.getId());
        v.setUserId(s.getUserId());
        v.setProblemId(s.getProblemId());
        v.setLanguage(s.getLanguage());
        if (includeCode) v.setCode(s.getCode());
        v.setVerdict(s.getVerdict());
        v.setTimeMs(s.getTimeMs());
        v.setMemoryKb(s.getMemoryKb());
        v.setMessage(s.getMessage());
        v.setPassed(s.getPassed());
        v.setTotal(s.getTotal());
        v.setCreatedAt(s.getCreatedAt());

        // Attach problem brief
        try {
            ProblemEntity p = problemService.getEntityById(s.getProblemId());
            v.setProblem(new SubmissionView.ProblemBrief(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty()));
        } catch (Exception ignored) {}

        // Attach user brief
        UserEntity u = userMapper.selectById(s.getUserId());
        if (u != null) v.setUser(new SubmissionView.UserBrief(u.getId(), u.getUsername()));

        return v;
    }
}
