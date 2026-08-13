package com.oj.reliability;

import com.oj.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JudgeOutboxRelayTest {

    private JudgeOutboxRepository repository;
    private ConfirmedJudgePublisher publisher;
    private OutboxPublisherStatus status;
    private JudgeOutboxRelay relay;
    private JudgeOutboxEvent event;

    @BeforeEach
    void setUp() {
        repository = mock(JudgeOutboxRepository.class);
        publisher = mock(ConfirmedJudgePublisher.class);
        status = new OutboxPublisherStatus();
        AppProperties properties = new AppProperties();
        properties.getOutbox().setInitialRetryDelay(Duration.ofSeconds(1));
        properties.getOutbox().setMaxRetryDelay(Duration.ofSeconds(30));
        relay = new JudgeOutboxRelay(repository, publisher, properties, status);
        event = new JudgeOutboxEvent(1, UUID.randomUUID(), 10, "{}", 1, UUID.randomUUID());
        when(repository.claimBatch(20, Duration.ofSeconds(30))).thenReturn(List.of(event));
    }

    @Test
    void marksPublishedOnlyAfterPositiveConfirm() {
        when(publisher.publish(event)).thenReturn(ConfirmedJudgePublisher.PublishResult.ack());
        when(repository.markPublished(event)).thenReturn(1);

        relay.publishReady();

        verify(repository).markPublished(event);
        verify(repository, never()).markRetry(any(), any(), any());
        assertThat(status.getLastFailure()).isNull();
    }

    @Test
    void nackSchedulesBoundedRetryWithoutPublishingSuccess() {
        when(publisher.publish(event))
                .thenReturn(ConfirmedJudgePublisher.PublishResult.failed("RABBIT_NACK"));
        when(repository.markRetry(event, Duration.ofSeconds(1), "RABBIT_NACK")).thenReturn(1);

        relay.publishReady();

        verify(repository).markRetry(eq(event), eq(Duration.ofSeconds(1)), eq("RABBIT_NACK"));
        verify(repository, never()).markPublished(any());
        assertThat(status.getLastFailure()).isEqualTo("RABBIT_NACK");
    }

    @Test
    void publisherCrashLeavesLeaseForExpiryRecovery() {
        when(publisher.publish(event)).thenThrow(new IllegalStateException("crash injection"));

        relay.publishReady();

        verify(repository, never()).markPublished(any());
        verify(repository, never()).markRetry(any(), any(), any());
        assertThat(status.getLastFailure()).isEqualTo("OUTBOX_RELAY_FAILURE");
    }
}
