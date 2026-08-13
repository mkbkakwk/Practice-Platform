package com.oj.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private final AppProperties appProperties;

    public RabbitConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public DirectExchange judgeExchange() {
        return ExchangeBuilder.directExchange(appProperties.getRabbitmq().getExchange()).durable(true).build();
    }

    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(appProperties.getRabbitmq().getQueue())
                .deadLetterExchange(appProperties.getRabbitmq().getRetryExchange())
                .deadLetterRoutingKey(appProperties.getRabbitmq().getRetryRoutingKey())
                .build();
    }

    @Bean
    public Binding judgeBinding(
            @Qualifier("judgeQueue") Queue judgeQueue,
            @Qualifier("judgeExchange") DirectExchange judgeExchange) {
        return BindingBuilder.bind(judgeQueue).to(judgeExchange)
                .with(appProperties.getRabbitmq().getRoutingKey());
    }

    @Bean
    public DirectExchange judgeRetryExchange() {
        return ExchangeBuilder.directExchange(appProperties.getRabbitmq().getRetryExchange()).durable(true).build();
    }

    @Bean
    public Queue judgeRetryQueue() {
        return QueueBuilder.durable(appProperties.getRabbitmq().getRetryQueue())
                .ttl((int) appProperties.getRabbitmq().getRetryDelay().toMillis())
                .deadLetterExchange(appProperties.getRabbitmq().getExchange())
                .deadLetterRoutingKey(appProperties.getRabbitmq().getRoutingKey())
                .build();
    }

    @Bean
    public Binding judgeRetryBinding(
            @Qualifier("judgeRetryQueue") Queue judgeRetryQueue,
            @Qualifier("judgeRetryExchange") DirectExchange judgeRetryExchange) {
        return BindingBuilder.bind(judgeRetryQueue).to(judgeRetryExchange)
                .with(appProperties.getRabbitmq().getRetryRoutingKey());
    }

    @Bean
    public DirectExchange judgeDeadLetterExchange() {
        return ExchangeBuilder.directExchange(appProperties.getRabbitmq().getDeadLetterExchange()).durable(true).build();
    }

    @Bean
    public Queue judgeDeadLetterQueue() {
        return QueueBuilder.durable(appProperties.getRabbitmq().getDeadLetterQueue()).build();
    }

    @Bean
    public Binding judgeDeadLetterBinding(
            @Qualifier("judgeDeadLetterQueue") Queue judgeDeadLetterQueue,
            @Qualifier("judgeDeadLetterExchange") DirectExchange judgeDeadLetterExchange) {
        return BindingBuilder.bind(judgeDeadLetterQueue).to(judgeDeadLetterExchange)
                .with(appProperties.getRabbitmq().getDeadLetterRoutingKey());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
