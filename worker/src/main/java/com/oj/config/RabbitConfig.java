package com.oj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Names judgeRabbitNames(
            @Value("${oj.rabbitmq.exchange:oj.judge}") String exchange,
            @Value("${oj.rabbitmq.routing-key:oj.judge.submit}") String routingKey,
            @Value("${oj.rabbitmq.queue:oj.judge.queue}") String queue,
            @Value("${oj.rabbitmq.retry-exchange:oj.judge.retry}") String retryExchange,
            @Value("${oj.rabbitmq.retry-routing-key:oj.judge.retry}") String retryRoutingKey,
            @Value("${oj.rabbitmq.retry-queue:oj.judge.retry.queue}") String retryQueue,
            @Value("${oj.rabbitmq.dead-letter-exchange:oj.judge.dlx}") String deadLetterExchange,
            @Value("${oj.rabbitmq.dead-letter-routing-key:oj.judge.dead}") String deadLetterRoutingKey,
            @Value("${oj.rabbitmq.dead-letter-queue:oj.judge.dlq}") String deadLetterQueue,
            @Value("${oj.rabbitmq.retry-delay-ms:5000}") int retryDelayMs) {
        return new Names(exchange, routingKey, queue, retryExchange, retryRoutingKey,
                retryQueue, deadLetterExchange, deadLetterRoutingKey, deadLetterQueue, retryDelayMs);
}
    @Bean
    public DirectExchange judgeExchange(Names names) {
        return ExchangeBuilder.directExchange(names.exchange()).durable(true).build();
    }

    @Bean
    public Queue judgeQueue(Names names) {
        return QueueBuilder.durable(names.queue())
                .deadLetterExchange(names.retryExchange())
                .deadLetterRoutingKey(names.retryRoutingKey())
                .build();
    }

    @Bean
    public Binding judgeBinding(
            @Qualifier("judgeQueue") Queue judgeQueue,
            @Qualifier("judgeExchange") DirectExchange judgeExchange,
            Names names) {
        return BindingBuilder.bind(judgeQueue).to(judgeExchange).with(names.routingKey());
    }

    @Bean
    public DirectExchange judgeRetryExchange(Names names) {
        return ExchangeBuilder.directExchange(names.retryExchange()).durable(true).build();
    }

    @Bean
    public Queue judgeRetryQueue(Names names) {
        return QueueBuilder.durable(names.retryQueue())
                .ttl(names.retryDelayMs())
                .deadLetterExchange(names.exchange())
                .deadLetterRoutingKey(names.routingKey())
                .build();
    }

    @Bean
    public Binding judgeRetryBinding(
            @Qualifier("judgeRetryQueue") Queue judgeRetryQueue,
            @Qualifier("judgeRetryExchange") DirectExchange judgeRetryExchange,
            Names names) {
        return BindingBuilder.bind(judgeRetryQueue).to(judgeRetryExchange)
                .with(names.retryRoutingKey());
    }

    @Bean
    public DirectExchange judgeDeadLetterExchange(Names names) {
        return ExchangeBuilder.directExchange(names.deadLetterExchange()).durable(true).build();
    }

    @Bean
    public Queue judgeDeadLetterQueue(Names names) {
        return QueueBuilder.durable(names.deadLetterQueue()).build();
    }

    @Bean
    public Binding judgeDeadLetterBinding(
            @Qualifier("judgeDeadLetterQueue") Queue judgeDeadLetterQueue,
            @Qualifier("judgeDeadLetterExchange") DirectExchange judgeDeadLetterExchange,
            Names names) {
        return BindingBuilder.bind(judgeDeadLetterQueue).to(judgeDeadLetterExchange)
                .with(names.deadLetterRoutingKey());
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    public record Names(
            String exchange,
            String routingKey,
            String queue,
            String retryExchange,
            String retryRoutingKey,
            String retryQueue,
            String deadLetterExchange,
            String deadLetterRoutingKey,
            String deadLetterQueue,
            int retryDelayMs) {
    }
}
