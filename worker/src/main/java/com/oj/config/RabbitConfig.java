package com.oj.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the judge exchange/queue/binding so the worker can create them
 * itself on startup (idempotent — safe even if the backend already did).
 */
@Configuration
public class RabbitConfig {

    @Value("${oj.rabbitmq.exchange:oj.judge}")
    private String exchange;

    @Value("${oj.rabbitmq.routing-key:oj.judge.submit}")
    private String routingKey;

    @Value("${oj.rabbitmq.queue:oj.judge.queue}")
    private String queue;

    @Bean
    public DirectExchange judgeExchange() {
        return ExchangeBuilder.directExchange(exchange).durable(true).build();
    }

    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding judgeBinding(Queue judgeQueue, DirectExchange judgeExchange) {
        return BindingBuilder.bind(judgeQueue).to(judgeExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
