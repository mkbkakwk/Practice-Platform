package com.oj.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.judge.JudgeService;
import com.oj.reliability.JudgeClaim;
import com.oj.reliability.JudgeMessage;
import com.oj.reliability.JudgeMessageRouter;
import com.oj.reliability.JudgeReliabilityProperties;
import com.oj.reliability.JudgeSubmissionRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JudgeConsumer {

    private static final Logger log = LoggerFactory.getLogger(JudgeConsumer.class);

    private final JudgeSubmissionRepository submissions;
    private final JudgeService judgeService;
    private final JudgeMessageRouter router;
    private final JudgeReliabilityProperties properties;
    private final RabbitConfig.Names rabbitNames;
    private final ObjectMapper objectMapper;

    public JudgeConsumer(
            JudgeSubmissionRepository submissions,
            JudgeService judgeService,
            JudgeMessageRouter router,
            JudgeReliabilityProperties properties,
            RabbitConfig.Names rabbitNames,
            ObjectMapper objectMapper) {
        this.submissions = submissions;
        this.judgeService = judgeService;
        this.router = router;
        this.properties = properties;
        this.rabbitNames = rabbitNames;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${oj.rabbitmq.queue:oj.judge.queue}")
    public void onRabbitMessage(Message amqpMessage, Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        List<Map<String, Object>> deaths = deathHeaders(
                amqpMessage.getMessageProperties().getHeader("x-death"));
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(
                    amqpMessage.getBody(), new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException | IOException exception) {
            malformed(deliveryTag, deaths, channel);
            return;
        }
        onMessage(payload, deliveryTag, deaths, channel);
    }

    public void onMessage(
            Map<String, Object> payload,
            long deliveryTag,
            List<Map<String, Object>> deaths,
            Channel channel) throws IOException {
        JudgeMessage message;
        try {
            message = JudgeMessage.from(payload);
        } catch (RuntimeException exception) {
            malformed(deliveryTag, deaths, channel);
            return;
        }

        int deliveryAttempt = Math.max(message.deliveryAttempt(), deathCount(deaths));
        message = message.retry(deliveryAttempt);
        UUID judgeToken = UUID.randomUUID();
        JudgeClaim claim;
        try {
            claim = submissions.claim(message.submissionId(), message.judgeGeneration(), judgeToken, properties.getLease());
        } catch (RuntimeException exception) {
            log.warn("Judge claim unavailable eventId={} submissionId={} attempt={} type={}",
                    message.eventId(), message.submissionId(), deliveryAttempt,
                    exception.getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
            return;
        }

        switch (claim.status()) {
            case NOT_FOUND -> {
                log.info("Judge message ignored deleted submission eventId={} submissionId={}",
                        message.eventId(), message.submissionId());
                channel.basicAck(deliveryTag, false);
            }
            case FINAL -> {
                log.info("Judge duplicate ignored final submission eventId={} submissionId={} attempt={}",
                        message.eventId(), message.submissionId(), deliveryAttempt);
                channel.basicAck(deliveryTag, false);
            }
            case STALE -> {
                log.info("Judge stale generation ignored eventId={} submissionId={} generation={}",
                        message.eventId(), message.submissionId(), message.judgeGeneration());
                channel.basicAck(deliveryTag, false);
            }
            case BUSY -> scheduleBusy(message, deliveryTag, channel);
            case CLAIMED -> executeClaimed(message, claim, deliveryTag, channel);
        }
    }

    private void executeClaimed(
            JudgeMessage message,
            JudgeClaim claim,
            long deliveryTag,
            Channel channel) throws IOException {
        var work = claim.work();
        log.info("Judge claim success eventId={} submissionId={} judgeToken={} attempt={} workerInstance={}",
                message.eventId(), message.submissionId(), claim.judgeToken(),
                message.deliveryAttempt(), properties.getWorkerInstance());
        JudgeService.JudgeResult result;
        try {
            result = judgeService.judge(
                    work.language(), work.code(), work.timeLimitMs(),
                    work.memoryLimitKb(), work.testCasesJson());
        } catch (RuntimeException exception) {
            log.warn("Judge infrastructure failure eventId={} submissionId={} judgeToken={} attempt={} type={}",
                    message.eventId(), message.submissionId(), claim.judgeToken(),
                    message.deliveryAttempt(), exception.getClass().getSimpleName());
            routeClaimedFailure(message, claim.judgeToken(), "WORKER_INFRASTRUCTURE_ERROR",
                    deliveryTag, channel, false);
            return;
        }

        if ("SE".equals(result.verdict)) {
            String category = "No test cases configured".equals(result.message)
                    ? "INVALID_PROBLEM_CONFIGURATION" : "RUNNER_UNAVAILABLE";
            routeClaimedFailure(message, claim.judgeToken(), category, deliveryTag, channel,
                    "INVALID_PROBLEM_CONFIGURATION".equals(category));
            return;
        }

        try {
            int updated = submissions.complete(message.submissionId(), message.judgeGeneration(), claim.judgeToken(), result);
            if (updated == 1) {
                log.info("Judge result committed eventId={} submissionId={} judgeToken={} requestId={} verdict={}",
                        message.eventId(), message.submissionId(), claim.judgeToken(),
                        result.requestId, result.verdict);
                channel.basicAck(deliveryTag, false);
            } else {
                // A generation may have been superseded while the Runner was
                // executing.  Retrying this already-computed result cannot make
                // it authoritative again: claim() will reject it as stale (or
                // final).  Acknowledge instead of adding needless retry traffic.
                log.info("Judge result ownership lost; completion is stale or duplicate eventId={} submissionId={} judgeToken={}",
                        message.eventId(), message.submissionId(), claim.judgeToken());
                channel.basicAck(deliveryTag, false);
            }
        } catch (RuntimeException exception) {
            log.warn("Judge result commit unavailable eventId={} submissionId={} judgeToken={} type={}",
                    message.eventId(), message.submissionId(), claim.judgeToken(),
                    exception.getClass().getSimpleName());
            routeClaimedFailure(message, claim.judgeToken(), "DATABASE_UNAVAILABLE",
                    deliveryTag, channel, false);
        }
    }

    private void routeClaimedFailure(
            JudgeMessage message,
            UUID judgeToken,
            String category,
            long deliveryTag,
            Channel channel,
            boolean permanent) throws IOException {
        try {
            infrastructureFailure(message, judgeToken, category, deliveryTag, channel, permanent);
        } catch (RuntimeException exception) {
            log.warn("Judge failure routing unavailable eventId={} submissionId={} judgeToken={} category={} type={}",
                    message.eventId(), message.submissionId(), judgeToken, category,
                    exception.getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
        }
    }

    private void infrastructureFailure(
            JudgeMessage message,
            UUID judgeToken,
            String category,
            long deliveryTag,
            Channel channel,
            boolean permanent) throws IOException {
        int attempt = message.deliveryAttempt();
        if (!permanent && attempt + 1 < properties.getMaxRetries()) {
            submissions.releaseForRetry(message.submissionId(), message.judgeGeneration(), judgeToken, category);
            JudgeMessage retry = message.retry(attempt + 1);
            if (router.retry(retry, category)) {
                log.info("Judge retry scheduled eventId={} submissionId={} nextAttempt={} category={}",
                        message.eventId(), message.submissionId(), retry.deliveryAttempt(), category);
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicReject(deliveryTag, false);
            }
            return;
        }

        int totalAttempts = attempt + 1;
        if (!router.deadLetter(message, totalAttempts, category)) {
            submissions.releaseForRetry(message.submissionId(), message.judgeGeneration(), judgeToken, category);
            channel.basicReject(deliveryTag, false);
            return;
        }
        int failed = submissions.markFailed(message.submissionId(), message.judgeGeneration(), judgeToken, category);
        if (failed != 1) {
            log.warn("Judge final-failure ownership lost eventId={} submissionId={} judgeToken={} category={}",
                    message.eventId(), message.submissionId(), judgeToken, category);
        }
        log.error("Judge message dead-lettered eventId={} submissionId={} attempts={} category={}",
                message.eventId(), message.submissionId(), totalAttempts, category);
        channel.basicAck(deliveryTag, false);
    }

    private void scheduleBusy(JudgeMessage message, long deliveryTag, Channel channel) throws IOException {
        if (router.retry(message, "LEASE_BUSY")) {
            log.info("Judge duplicate delayed active lease eventId={} submissionId={} attempt={}",
                    message.eventId(), message.submissionId(), message.deliveryAttempt());
            channel.basicAck(deliveryTag, false);
        } else {
            channel.basicReject(deliveryTag, false);
        }
    }

    private int deathCount(List<Map<String, Object>> deaths) {
        if (deaths == null) return 0;
        long count = deaths.stream()
                .filter(death -> rabbitNames.queue().equals(String.valueOf(death.get("queue"))))
                .mapToLong(death -> death.get("count") instanceof Number number ? number.longValue() : 0)
                .max().orElse(0);
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private void malformed(
            long deliveryTag,
            List<Map<String, Object>> deaths,
            Channel channel) throws IOException {
        int attempts = deathCount(deaths);
        log.warn("Judge message malformed attempt={} workerInstance={}",
                attempts, properties.getWorkerInstance());
        if (router.malformed("INVALID_MESSAGE", attempts)) {
            channel.basicAck(deliveryTag, false);
        } else {
            channel.basicReject(deliveryTag, false);
        }
    }

    private List<Map<String, Object>> deathHeaders(Object header) {
        if (!(header instanceof List<?> values)) {
            return null;
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> death = (Map<String, Object>) value;
                    return death;
                })
                .toList();
    }
}
