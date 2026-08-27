package com.oj.controller;

import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final DataSource dataSource;
    private final RabbitListenerEndpointRegistry listeners;
    private final String runnerBaseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public WorkerHealthController(DataSource dataSource, RabbitListenerEndpointRegistry listeners,
                                  @Value("${oj.judge.runner.base-url:}") String runnerBaseUrl) {
        this.dataSource = dataSource;
        this.listeners = listeners;
        this.runnerBaseUrl = runnerBaseUrl;
    }

    @GetMapping("/health")
    public Map<String, String> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        boolean ready = databaseReachable() && listeners.isRunning() && runnerReady();
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", ready ? "UP" : "DOWN"));
    }

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
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }
}
