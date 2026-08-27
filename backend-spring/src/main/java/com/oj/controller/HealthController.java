package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;
    private final Flyway flyway;
    private final String gitSha;
    private final String buildVersion;
    private final String buildTime;

    public HealthController(DataSource dataSource, Flyway flyway,
                            @Value("${oj.build.git-sha:unknown}") String gitSha,
                            @Value("${oj.build.version:dev}") String buildVersion,
                            @Value("${oj.build.time:unknown}") String buildTime) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.gitSha = gitSha;
        this.buildVersion = buildVersion;
        this.buildTime = buildTime;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    /** Minimal readiness for Docker and reverse-proxy checks; it intentionally exposes no dependency detail. */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean ready = databaseReachable() && flywayCurrentVersion() != null;
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", ready ? "UP" : "DOWN"));
    }

    @GetMapping("/admin/version")
    public Map<String, Object> version() {
        if (!CurrentUser.isAdmin()) throw ApiException.forbidden("需要管理员权限");
        String flywayVersion = flywayCurrentVersion();
        return Map.of(
                "gitSha", gitSha,
                "version", buildVersion,
                "buildTime", buildTime,
                "flywayVersion", flywayVersion == null ? "unknown" : flywayVersion);
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String flywayCurrentVersion() {
        try {
            var current = flyway.info().current();
            return current == null ? null : current.getVersion().getVersion();
        } catch (Exception ignored) {
            return null;
        }
    }
}
