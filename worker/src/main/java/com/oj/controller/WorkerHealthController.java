package com.oj.controller;

import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.oj.observability.WorkerOperationalMetrics;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;

/** Internal container health endpoints. They intentionally expose status only. */
@RestController
@RequestMapping("/api")
public class WorkerHealthController {
    private static final Duration RUNNER_READINESS_TIMEOUT = Duration.ofMillis(750);
    private final DataSource dataSource;
    private final BoundedReadinessProbe databaseProbe;
    private final RabbitListenerEndpointRegistry listeners;
    private final RabbitConnectivityReadinessProbe rabbitConnectivityProbe;
    private final String runnerBaseUrl;
    private final WorkerOperationalMetrics metrics;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    @Autowired
    public WorkerHealthController(DataSource dataSource, BoundedReadinessProbe databaseProbe,
                                  RabbitListenerEndpointRegistry listeners,
                                  RabbitConnectivityReadinessProbe rabbitConnectivityProbe,
                                  @Value("${oj.judge.runner.base-url:}") String runnerBaseUrl,
                                  WorkerOperationalMetrics metrics) {
        this.dataSource = dataSource;
        this.databaseProbe = databaseProbe;
        this.listeners = listeners;
        this.rabbitConnectivityProbe = rabbitConnectivityProbe;
        this.runnerBaseUrl = runnerBaseUrl;
        this.metrics = metrics;
    }

    /** Preserves the focused readiness-test constructor without changing readiness semantics. */
    WorkerHealthController(DataSource dataSource, BoundedReadinessProbe databaseProbe,
                           RabbitListenerEndpointRegistry listeners,
                           RabbitConnectivityReadinessProbe rabbitConnectivityProbe,
                           String runnerBaseUrl) {
        this(dataSource, databaseProbe, listeners, rabbitConnectivityProbe, runnerBaseUrl,
                new WorkerOperationalMetrics());
    }

    @GetMapping("/health")
    public Map<String, String> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        boolean ready = databaseProbe.check(this::databaseReachable)
                && listeners.isRunning()
                && rabbitConnectivityProbe.brokerReachable()
                && runnerReady();
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", ready ? "UP" : "DOWN"));
    }

    /** Internal Docker-network endpoint; the backend status view is the external admin surface. */
    @GetMapping("/metrics")
    public Map<String, Long> metrics() { return metrics.snapshot(); }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean runnerReady() {
        if (runnerBaseUrl == null || runnerBaseUrl.isBlank()) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(runnerBaseUrl + "/api/readiness"))
                    .timeout(RUNNER_READINESS_TIMEOUT).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }
}
