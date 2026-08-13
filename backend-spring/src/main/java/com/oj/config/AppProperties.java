package com.oj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "oj")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private boolean promoteFirstAdmin = false;
    private RabbitMq rabbitmq = new RabbitMq();
    private Outbox outbox = new Outbox();
    private String docStorage = "/tmp/oj-docs";

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public boolean isPromoteFirstAdmin() { return promoteFirstAdmin; }
    public void setPromoteFirstAdmin(boolean promoteFirstAdmin) { this.promoteFirstAdmin = promoteFirstAdmin; }
    public RabbitMq getRabbitmq() { return rabbitmq; }
    public void setRabbitmq(RabbitMq rabbitmq) { this.rabbitmq = rabbitmq; }
    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public String getDocStorage() { return docStorage; }
    public void setDocStorage(String docStorage) { this.docStorage = docStorage; }

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
        private String retryExchange = "oj.judge.retry";
        private String retryRoutingKey = "oj.judge.retry";
        private String retryQueue = "oj.judge.retry.queue";
        private String deadLetterExchange = "oj.judge.dlx";
        private String deadLetterRoutingKey = "oj.judge.dead";
        private String deadLetterQueue = "oj.judge.dlq";
        private Duration retryDelay = Duration.ofSeconds(5);
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        public String getQueue() { return queue; }
        public void setQueue(String queue) { this.queue = queue; }
        public String getRetryExchange() { return retryExchange; }
        public void setRetryExchange(String retryExchange) { this.retryExchange = retryExchange; }
        public String getRetryRoutingKey() { return retryRoutingKey; }
        public void setRetryRoutingKey(String retryRoutingKey) { this.retryRoutingKey = retryRoutingKey; }
        public String getRetryQueue() { return retryQueue; }
        public void setRetryQueue(String retryQueue) { this.retryQueue = retryQueue; }
        public String getDeadLetterExchange() { return deadLetterExchange; }
        public void setDeadLetterExchange(String deadLetterExchange) { this.deadLetterExchange = deadLetterExchange; }
        public String getDeadLetterRoutingKey() { return deadLetterRoutingKey; }
        public void setDeadLetterRoutingKey(String deadLetterRoutingKey) { this.deadLetterRoutingKey = deadLetterRoutingKey; }
        public String getDeadLetterQueue() { return deadLetterQueue; }
        public void setDeadLetterQueue(String deadLetterQueue) { this.deadLetterQueue = deadLetterQueue; }
        public Duration getRetryDelay() { return retryDelay; }
        public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }
    }

    public static class Outbox {
        private int batchSize = 20;
        private Duration pollDelay = Duration.ofMillis(500);
        private Duration lease = Duration.ofSeconds(30);
        private Duration confirmTimeout = Duration.ofSeconds(5);
        private Duration initialRetryDelay = Duration.ofSeconds(1);
        private Duration maxRetryDelay = Duration.ofMinutes(1);
        private Duration retention = Duration.ofDays(7);
        private Duration cleanupInterval = Duration.ofHours(1);
        private int cleanupBatchSize = 500;
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public Duration getPollDelay() { return pollDelay; }
        public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
        public Duration getLease() { return lease; }
        public void setLease(Duration lease) { this.lease = lease; }
        public Duration getConfirmTimeout() { return confirmTimeout; }
        public void setConfirmTimeout(Duration confirmTimeout) { this.confirmTimeout = confirmTimeout; }
        public Duration getInitialRetryDelay() { return initialRetryDelay; }
        public void setInitialRetryDelay(Duration initialRetryDelay) { this.initialRetryDelay = initialRetryDelay; }
        public Duration getMaxRetryDelay() { return maxRetryDelay; }
        public void setMaxRetryDelay(Duration maxRetryDelay) { this.maxRetryDelay = maxRetryDelay; }
        public Duration getRetention() { return retention; }
        public void setRetention(Duration retention) { this.retention = retention; }
        public Duration getCleanupInterval() { return cleanupInterval; }
        public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
        public int getCleanupBatchSize() { return cleanupBatchSize; }
        public void setCleanupBatchSize(int cleanupBatchSize) { this.cleanupBatchSize = cleanupBatchSize; }
    }
}
