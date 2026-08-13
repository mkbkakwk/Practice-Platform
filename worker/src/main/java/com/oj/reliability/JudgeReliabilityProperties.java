package com.oj.reliability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "oj.judge-reliability")
public class JudgeReliabilityProperties {
    private int maxRetries = 3;
    private Duration lease = Duration.ofMinutes(30);
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private String workerInstance = defaultWorkerInstance();

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getLease() { return lease; }
    public void setLease(Duration lease) { this.lease = lease; }
    public Duration getConfirmTimeout() { return confirmTimeout; }
    public void setConfirmTimeout(Duration confirmTimeout) { this.confirmTimeout = confirmTimeout; }
    public String getWorkerInstance() { return workerInstance; }
    public void setWorkerInstance(String workerInstance) { this.workerInstance = workerInstance; }

    private static String defaultWorkerInstance() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "worker-" + UUID.randomUUID();
        }
    }
}
