package com.oj.reliability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.config.AppProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitConfirmedJudgePublisher implements ConfirmedJudgePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public RabbitConfirmedJudgePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AppProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public PublishResult publish(JudgeOutboxEvent event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(event.payload(), Map.class);
            CorrelationData correlation = new CorrelationData(event.eventId().toString());
            rabbitTemplate.convertAndSend(
                    properties.getRabbitmq().getExchange(),
                    properties.getRabbitmq().getRoutingKey(),
                    payload,
                    message -> {
                        var headers = message.getMessageProperties();
                        headers.setMessageId(event.eventId().toString());
                        headers.setCorrelationId(Integer.toString(event.submissionId()));
                        headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        headers.setHeader("eventId", event.eventId().toString());
                        headers.setHeader("submissionId", event.submissionId());
                        return message;
                    },
                    correlation);
            Duration timeout = properties.getOutbox().getConfirmTimeout();
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                return PublishResult.failed("RABBIT_NACK");
            }
            if (correlation.getReturned() != null) {
                return PublishResult.failed("UNROUTABLE");
            }
            return PublishResult.ack();
        } catch (JsonProcessingException exception) {
            return PublishResult.failed("INVALID_OUTBOX_PAYLOAD");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PublishResult.failed("PUBLISH_INTERRUPTED");
        } catch (Exception exception) {
            return PublishResult.failed("PUBLISH_UNAVAILABLE");
        }
    }
}
