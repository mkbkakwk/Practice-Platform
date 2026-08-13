package com.oj.reliability;

import com.oj.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class JudgeMessageRouterTest {

    private RabbitTemplate rabbitTemplate;
    private JudgeMessageRouter router;
    private AtomicReference<Message> publishedMessage;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        RabbitConfig.Names names = new RabbitConfig.Names(
                "judge.exchange", "judge.submit", "judge.queue",
                "judge.retry", "judge.retry", "judge.retry.queue",
                "judge.dlx", "judge.dead", "judge.dlq", 1000);
        JudgeReliabilityProperties properties = new JudgeReliabilityProperties();
        properties.setConfirmTimeout(Duration.ofSeconds(1));
        router = new JudgeMessageRouter(rabbitTemplate, names, properties);
        publishedMessage = new AtomicReference<>();
    }

    @Test
    void retryUsesPersistentMessageIdentityAndWaitsForConfirm() {
        completePublishWith(true);
        UUID eventId = UUID.randomUUID();

        boolean published = router.retry(new JudgeMessage(eventId, 42, 1, 2), "RUNNER_UNAVAILABLE");

        assertThat(published).isTrue();
        MessageProperties headers = publishedMessage.get().getMessageProperties();
        assertThat(headers.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(headers.getMessageId()).isEqualTo(eventId.toString());
        assertThat(headers.getCorrelationId()).isEqualTo("42");
        assertThat(headers.getHeader("failureCategory").toString())
                .isEqualTo("RUNNER_UNAVAILABLE");
    }

    @Test
    void nackDoesNotAuthorizeOriginalMessageAck() {
        completePublishWith(false);

        assertThat(router.retry(new JudgeMessage(UUID.randomUUID(), 42, 1, 0), "RABBIT_NACK"))
                .isFalse();
    }

    @Test
    void deadLetterPayloadSerializesWithTheRuntimeJsonConverter() {
        JudgeDeadLetter deadLetter = new JudgeDeadLetter(
                UUID.randomUUID(), 42, "judge.submit", 3,
                "RUNNER_UNAVAILABLE", "2026-08-13T00:00:00Z");

        Message encoded = new Jackson2JsonMessageConverter()
                .toMessage(deadLetter, new MessageProperties());

        assertThat(new String(encoded.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"attemptCount\":3")
                .contains("\"failedAt\":\"2026-08-13T00:00:00Z\"");
    }

    private void completePublishWith(boolean ack) {
        doAnswer(invocation -> {
            MessagePostProcessor processor = invocation.getArgument(3);
            CorrelationData correlation = invocation.getArgument(4);
            Message message = processor.postProcessMessage(
                    new Message(new byte[0], new MessageProperties()));
            publishedMessage.set(message);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }
}
