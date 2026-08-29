package com.oj.observability;

import com.oj.config.AppProperties;
import com.oj.reliability.JudgeOutboxRepository;
import com.oj.reliability.OutboxPublisherStatus;
import org.flywaydb.core.Flyway;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Produces an admin-only, bounded, read-only operational snapshot. */
@Service
public class SystemStatusService {
    private final DataSource dataSource;
    private final Flyway flyway;
    private final RabbitTemplate rabbitTemplate;
    private final AppProperties properties;
    private final JudgeOutboxRepository outbox;
    private final OutboxPublisherStatus publisherStatus;
    private final OperationalMetrics metrics;
    private final BoundedStatusProbe probe;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    private final String workerReadinessUrl;
    private final String runnerReadinessUrl;
    private final String gitSha;
    private final String version;
    private final String buildTime;

    public SystemStatusService(DataSource dataSource, Flyway flyway, RabbitTemplate rabbitTemplate,
                               AppProperties properties, JudgeOutboxRepository outbox,
                               OutboxPublisherStatus publisherStatus, OperationalMetrics metrics,
                               BoundedStatusProbe probe,
                               @Value("${oj.status.worker-readiness-url:http://worker:8081/api/readiness}") String workerReadinessUrl,
                               @Value("${oj.status.runner-readiness-url:http://runner:8080/api/readiness}") String runnerReadinessUrl,
                               @Value("${oj.build.git-sha:unknown}") String gitSha,
                               @Value("${oj.build.version:dev}") String version,
                               @Value("${oj.build.time:unknown}") String buildTime) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.outbox = outbox;
        this.publisherStatus = publisherStatus;
        this.metrics = metrics;
        this.probe = probe;
        this.workerReadinessUrl = workerReadinessUrl;
        this.runnerReadinessUrl = runnerReadinessUrl;
        this.gitSha = gitSha;
        this.version = version;
        this.buildTime = buildTime;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("backend", component(probe.check(() -> databaseReachable() && flywayVersion() != null)));
        BoundedStatusProbe.ProbeResult rabbit = probe.check(this::rabbitReachable);
        components.put("postgresql", component(probe.check(this::databaseReachable)));
        components.put("rabbitmq", component(rabbit));
        components.put("worker", component(probe.check(() -> httpReady(workerReadinessUrl))));
        BoundedStatusProbe.ProbeResult runner = probe.check(() -> httpReady(runnerReadinessUrl));
        Map<String, Object> runnerComponent = new LinkedHashMap<>(component(runner));
        // Runner readiness is deliberately defined by Stage 9A as sandbox availability.
        runnerComponent.put("sandboxAvailable", runner.up());
        components.put("runner", runnerComponent);

        Map<String, Object> queues = new LinkedHashMap<>();
        if (rabbit.up()) {
            queues.put("main", queueCount(properties.getRabbitmq().getQueue()));
            queues.put("retry", queueCount(properties.getRabbitmq().getRetryQueue()));
            queues.put("dlq", queueCount(properties.getRabbitmq().getDeadLetterQueue()));
        } else {
            queues.put("main", "UNKNOWN"); queues.put("retry", "UNKNOWN"); queues.put("dlq", "UNKNOWN");
        }
        Map<String, Object> outboxState = new LinkedHashMap<>();
        AtomicLong nonterminal = new AtomicLong(-1);
        BoundedStatusProbe.ProbeResult outboxProbe = probe.check(() -> {
            nonterminal.set(outbox.pendingCount());
            return true;
        });
        outboxState.put("status", outboxProbe.up() ? "UP" : "UNKNOWN");
        outboxState.put("latencyMs", outboxProbe.latencyMs());
        if (outboxProbe.up()) outboxState.put("nonterminal", nonterminal.get());
        outboxState.put("publisherRunning", publisherStatus.isRunning());
        outboxState.put("lastFailure", publisherStatus.getLastFailure() == null ? "NONE" : publisherStatus.getLastFailure());

        return Map.of(
                "checkedAt", Instant.now().toString(),
                "version", Map.of("gitSha", gitSha, "version", version, "buildTime", buildTime,
                        "flywayVersion", safeFlywayVersion()),
                "components", components,
                "queues", queues,
                "outbox", outboxState,
                "metrics", metrics.snapshot());
    }

    private Map<String, Object> component(BoundedStatusProbe.ProbeResult result) {
        return Map.of("status", result.up() ? "UP" : "DOWN", "latencyMs", result.latencyMs());
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) { return connection.isValid(1); }
        catch (Exception ignored) { return false; }
    }

    private boolean rabbitReachable() {
        try { return rabbitTemplate.execute(channel -> channel.isOpen()); }
        catch (Exception ignored) { return false; }
    }

    private boolean httpReady(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(750)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) { return false; }
    }

    private long queueCount(String queue) {
        try { return rabbitTemplate.execute(channel -> channel.messageCount(queue)); }
        catch (Exception ignored) { return -1; }
    }

    private String flywayVersion() {
        try { var current = flyway.info().current(); return current == null ? null : current.getVersion().getVersion(); }
        catch (Exception ignored) { return null; }
    }

    private String safeFlywayVersion() { String value = flywayVersion(); return value == null ? "unknown" : value; }
}
