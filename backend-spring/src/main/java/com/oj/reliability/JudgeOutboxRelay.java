package com.oj.reliability;

import com.oj.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "oj.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JudgeOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(JudgeOutboxRelay.class);

    private final JudgeOutboxRepository repository;
    private final ConfirmedJudgePublisher publisher;
    private final AppProperties properties;
    private final OutboxPublisherStatus status;

    public JudgeOutboxRelay(
            JudgeOutboxRepository repository,
            ConfirmedJudgePublisher publisher,
            AppProperties properties,
            OutboxPublisherStatus status) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.status = status;
    }

    @Scheduled(
            initialDelayString = "${OUTBOX_POLL_DELAY_MS:500}",
            fixedDelayString = "${OUTBOX_POLL_DELAY_MS:500}")
    public void publishReady() {
        status.pollStarted();
        try {
            var events = repository.claimBatch(
                    properties.getOutbox().getBatchSize(),
                    properties.getOutbox().getLease());
            for (JudgeOutboxEvent event : events) {
                publishOne(event);
            }
        } catch (RuntimeException exception) {
            status.failed("OUTBOX_RELAY_FAILURE");
            log.warn("Judge outbox poll failed type={}", exception.getClass().getSimpleName());
        } finally {
            status.pollFinished();
        }
    }

    private void publishOne(JudgeOutboxEvent event) {
        ConfirmedJudgePublisher.PublishResult result = publisher.publish(event);
        if (result.confirmed()) {
            if (repository.markPublished(event) != 1) {
                status.failed("OUTBOX_OWNERSHIP_LOST");
                log.warn("Judge outbox confirm ownership lost eventId={} submissionId={}",
                        event.eventId(), event.submissionId());
                return;
            }
            status.confirmed();
            log.info("Judge outbox publish confirmed eventId={} submissionId={} attempt={}",
                    event.eventId(), event.submissionId(), event.attemptCount());
            return;
        }

        Duration delay = retryDelay(event.attemptCount());
        if (repository.markRetry(event, delay, result.failureCategory()) != 1) {
            status.failed("OUTBOX_OWNERSHIP_LOST");
            log.warn("Judge outbox retry ownership lost eventId={} submissionId={}",
                    event.eventId(), event.submissionId());
            return;
        }
        status.failed(result.failureCategory());
        log.warn("Judge outbox publish retry eventId={} submissionId={} attempt={} category={} delayMs={}",
                event.eventId(), event.submissionId(), event.attemptCount(),
                result.failureCategory(), delay.toMillis());
    }

    Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 10);
        long delay = Math.min(
                properties.getOutbox().getMaxRetryDelay().toMillis(),
                Math.multiplyExact(properties.getOutbox().getInitialRetryDelay().toMillis(), multiplier));
        return Duration.ofMillis(delay);
    }

    @Scheduled(
            initialDelayString = "${OUTBOX_CLEANUP_INTERVAL_MS:3600000}",
            fixedDelayString = "${OUTBOX_CLEANUP_INTERVAL_MS:3600000}")
    public void cleanupPublished() {
        int deleted = repository.deletePublishedBefore(
                properties.getOutbox().getRetention(),
                properties.getOutbox().getCleanupBatchSize());
        if (deleted > 0) {
            log.info("Judge outbox retention removed count={}", deleted);
        }
    }
}
