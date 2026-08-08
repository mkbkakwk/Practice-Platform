package com.oj.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
        return QueueBuilder.durable(appProperties.getRabbitmq().getQueue()).build();
    }

    @Bean
    public Binding judgeBinding(Queue judgeQueue, DirectExchange judgeExchange) {
        return BindingBuilder.bind(judgeQueue).to(judgeExchange)
                .with(appProperties.getRabbitmq().getRoutingKey());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
