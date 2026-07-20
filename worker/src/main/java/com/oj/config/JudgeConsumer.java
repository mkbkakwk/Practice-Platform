package com.oj.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.entity.SubmissionEntity;
import com.oj.entity.UserEntity;
import com.oj.judge.JudgeService;
import com.oj.mapper.SubmissionMapper;
import com.oj.mapper.UserMapper;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class JudgeConsumer {

    private static final Logger log = LoggerFactory.getLogger(JudgeConsumer.class);

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ObjectMapper mapper = new ObjectMapper();
    private JudgeService judgeService;

    @Value("${oj.judge.workspace:/tmp/oj-judge}")
    private String workspacePath;

    public JudgeConsumer(SubmissionMapper submissionMapper, UserMapper userMapper) {
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        judgeService = new JudgeService(Path.of(workspacePath));
        log.info("[worker] judge workspace: {}", workspacePath);
    }

    @RabbitListener(queues = "${oj.rabbitmq.queue:oj.judge.queue}")
    public void onMessage(@Payload java.util.Map<String, Object> payload, @Header(AmqpHeaders.DELIVERY_TAG) long tag, Channel channel)
            throws IOException {
        int submissionId = ((Number) payload.get("submissionId")).intValue();
        String language = (String) payload.get("language");
        String code = (String) payload.get("code");
        long timeLimitMs = ((Number) payload.get("timeLimitMs")).longValue();
        long memoryLimitKb = ((Number) payload.get("memoryLimitKb")).longValue();
        String testCasesJson = (String) payload.get("testCasesJson");

        log.info("[worker] judging submission #{} lang={} timeLimit={}ms", submissionId, language, timeLimitMs);

        try {
            JudgeService.JudgeResult result = judgeService.judge(language, code, timeLimitMs, memoryLimitKb, testCasesJson);

            SubmissionEntity s = new SubmissionEntity();
            s.setId(submissionId);
            s.setVerdict(result.verdict);
            s.setPassed(result.passed);
            s.setTotal(result.total);
            s.setTimeMs((int) Math.min(result.timeMs, Integer.MAX_VALUE));
            s.setMemoryKb((int) Math.min(result.memoryKb, Integer.MAX_VALUE));
            s.setMessage(result.message);
            submissionMapper.updateById(s);

            // On first AC for this user/problem, bump solvedCount.
            if ("AC".equals(result.verdict)) {
                SubmissionEntity cur = submissionMapper.selectById(submissionId);
                if (cur != null) {
                    Long priorAc = submissionMapper.selectCount(new QueryWrapper<SubmissionEntity>()
                            .eq("user_id", cur.getUserId())
                            .eq("problem_id", cur.getProblemId())
                            .eq("verdict", "AC")
                            .ne("id", submissionId));
                    if (priorAc == null || priorAc == 0) {
                        userMapper.incrementSolved(cur.getUserId());
                    }
                }
            }

            log.info("[worker] submission #{} verdict={} passed={}/{} timeMs={}",
                    submissionId, result.verdict, result.passed, result.total, result.timeMs);

            // ack on success
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[worker] submission #{} judge error", submissionId, e);
            try {
                SubmissionEntity s = new SubmissionEntity();
                s.setId(submissionId);
                s.setVerdict("SE");
                s.setMessage("评测器异常: " + e.getMessage());
                submissionMapper.updateById(s);
            } catch (Exception ignored) {}
            // ack even on error to avoid poison-message loop (could add DLQ later)
            channel.basicAck(tag, false);
        }
    }
}
