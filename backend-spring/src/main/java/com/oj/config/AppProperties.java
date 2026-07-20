package com.oj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "oj")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private boolean promoteFirstAdmin = true;
    private RabbitMq rabbitmq = new RabbitMq();

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public boolean isPromoteFirstAdmin() { return promoteFirstAdmin; }
    public void setPromoteFirstAdmin(boolean promoteFirstAdmin) { this.promoteFirstAdmin = promoteFirstAdmin; }
    public RabbitMq getRabbitmq() { return rabbitmq; }
    public void setRabbitmq(RabbitMq rabbitmq) { this.rabbitmq = rabbitmq; }

    public static class Jwt {
        private String secret;
        private String expiresIn = "7d";
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getExpiresIn() { return expiresIn; }
        public void setExpiresIn(String expiresIn) { this.expiresIn = expiresIn; }
    }

    public static class Cors {
        private String origin = "*";
        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }
    }

    public static class RabbitMq {
        private String exchange = "oj.judge";
        private String routingKey = "oj.judge.submit";
        private String queue = "oj.judge.queue";
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        public String getQueue() { return queue; }
        public void setQueue(String queue) { this.queue = queue; }
    }
}
