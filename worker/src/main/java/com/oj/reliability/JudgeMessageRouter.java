package com.oj.reliability;

import com.oj.config.RabbitConfig;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class JudgeMessageRouter {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitConfig.Names names;
    private final JudgeReliabilityProperties properties;

    public JudgeMessageRouter(
            RabbitTemplate rabbitTemplate,
            RabbitConfig.Names names,
            JudgeReliabilityProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.names = names;
        this.properties = properties;
    }

    public boolean retry(JudgeMessage message, String failureCategory) {
        return publish(names.retryExchange(), names.retryRoutingKey(), message,
                message.eventId().toString(), message.submissionId(), failureCategory);
    }

    public boolean deadLetter(JudgeMessage message, int attempts, String failureCategory) {
        JudgeDeadLetter deadLetter = new JudgeDeadLetter(
                message.eventId(), message.submissionId(), names.routingKey(),
                attempts, failureCategory, Instant.now().toString());
        return publish(names.deadLetterExchange(), names.deadLetterRoutingKey(), deadLetter,
                message.eventId().toString(), message.submissionId(), failureCategory);
    }

    public boolean malformed(String failureCategory, int attempts) {
        JudgeDeadLetter deadLetter = new JudgeDeadLetter(
                null, null, names.routingKey(), attempts, failureCategory, Instant.now().toString());
        return publish(names.deadLetterExchange(), names.deadLetterRoutingKey(), deadLetter,
                null, null, failureCategory);
    }

    private boolean publish(
            String exchange,
            String routingKey,
            Object payload,
            String eventId,
            Integer submissionId,
            String failureCategory) {
        try {
            CorrelationData correlation = new CorrelationData(
                    eventId == null ? "malformed-" + System.nanoTime() : eventId);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, message -> {
                var headers = message.getMessageProperties();
                headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                headers.setMessageId(eventId);
                if (submissionId != null) {
                    headers.setCorrelationId(submissionId.toString());
                }
                headers.setHeader("failureCategory", failureCategory);
                return message;
            }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            return false;
        }
    }
}
