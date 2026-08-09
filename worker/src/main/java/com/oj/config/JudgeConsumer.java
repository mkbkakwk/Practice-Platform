package com.oj.config;

import com.oj.entity.SubmissionEntity;
import com.oj.judge.JudgeService;
import com.oj.mapper.SubmissionMapper;
import com.oj.mapper.UserMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class JudgeConsumer {

    private static final Logger log = LoggerFactory.getLogger(JudgeConsumer.class);

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final JudgeService judgeService;

    public JudgeConsumer(
            SubmissionMapper submissionMapper,
            UserMapper userMapper,
            JudgeService judgeService) {
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.judgeService = judgeService;
    }

    @RabbitListener(queues = "${oj.rabbitmq.queue:oj.judge.queue}")
    public void onMessage(@Payload Map<String, Object> payload,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          Channel channel) throws IOException {
        Integer submissionId = null;
        try {
            submissionId = ((Number) payload.get("submissionId")).intValue();
            SubmissionEntity queuedSubmission = submissionMapper.selectById(submissionId);
            if (queuedSubmission == null) {
                log.info("[worker] submission #{} was deleted before judging; ignoring message", submissionId);
                return;
            }

            String language = (String) payload.get("language");
            String code = (String) payload.get("code");
            long timeLimitMs = ((Number) payload.get("timeLimitMs")).longValue();
            long memoryLimitKb = ((Number) payload.get("memoryLimitKb")).longValue();
            String testCasesJson = (String) payload.get("testCasesJson");
            log.info("[worker] judging submission #{} lang={} timeLimit={}ms", submissionId, language, timeLimitMs);

            JudgeService.JudgeResult result = judgeService.judge(
                    language, code, timeLimitMs, memoryLimitKb, testCasesJson);

            SubmissionEntity update = new SubmissionEntity();
            update.setId(submissionId);
            update.setVerdict(result.verdict);
            update.setPassed(result.passed);
            update.setTotal(result.total);
            update.setTimeMs((int) Math.min(result.timeMs, Integer.MAX_VALUE));
            update.setMemoryKb((int) Math.min(result.memoryKb, Integer.MAX_VALUE));
            update.setMessage(result.message);
            int updatedRows = submissionMapper.updateById(update);
            if (updatedRows == 0) {
                log.info("[worker] submission #{} was deleted during judging; result ignored", submissionId);
                return;
            }

            if ("AC".equals(result.verdict)) {
                SubmissionEntity current = submissionMapper.selectById(submissionId);
                if (current != null) {
                    userMapper.recalculateSolved(current.getUserId());
                }
            }

            log.info("[worker] submission #{} requestId={} verdict={} passed={}/{} timeMs={}",
                    submissionId, result.requestId, result.verdict, result.passed, result.total, result.timeMs);
        } catch (Exception exception) {
            log.error("[worker] submission #{} judge error", submissionId, exception);
            if (submissionId != null) {
                try {
                    SubmissionEntity existing = submissionMapper.selectById(submissionId);
                    if (existing != null) {
                        SubmissionEntity update = new SubmissionEntity();
                        update.setId(submissionId);
                        update.setVerdict("SE");
                        update.setMessage("评测器异常: " + exception.getMessage());
                        submissionMapper.updateById(update);
                    }
                } catch (Exception updateException) {
                    log.warn("[worker] failed to persist SE for submission #{}: {}",
                            submissionId, updateException.getMessage());
                }
            }
        } finally {
            // Always acknowledge. Deleted or malformed submissions must not cause poison-message retries.
            channel.basicAck(deliveryTag, false);
        }
    }
}
