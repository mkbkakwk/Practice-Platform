package com.oj.controller;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @AfterEach
    void clearCurrentUser() {
        CurrentUser.clear();
    }

    @Test
    void publicLivenessIsMinimalAndDoesNotExposeOperationalDetails() {
        HealthController controller = controller(reachableDataSource(), flyway("9"));

        assertThat(controller.health()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
    }

    @Test
    void readinessIsUnavailableWhenDatabaseCannotBeReached() {
        DataSource unavailable = mock(DataSource.class);
        HealthController controller = controller(unavailable, flyway("9"));

        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(controller.readiness().getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "DOWN"));
    }

    @Test
    void readinessRequiresFlywayInitialization() {
        HealthController controller = controller(reachableDataSource(), flyway(null));

        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessDeliberatelyDoesNotRequireRabbitMqBecauseTheOutboxPersistsWork() {
        HealthController controller = controller(reachableDataSource(), flyway("9"));

        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void versionIsAdminOnlyAndContainsOnlyReleaseEvidence() {
        HealthController controller = controller(reachableDataSource(), flyway("9"));

        assertThatThrownBy(controller::version).isInstanceOf(ApiException.class);

        CurrentUser.set(1, "admin", "ADMIN");
        assertThat(controller.version()).containsExactlyEntriesOf(java.util.Map.of(
                "gitSha", "release-sha", "version", "release-version",
                "buildTime", "2026-08-27T00:00:00Z", "flywayVersion", "9"));
    }

    private HealthController controller(DataSource dataSource, Flyway flyway) {
        return new HealthController(dataSource, flyway,
                "release-sha", "release-version", "2026-08-27T00:00:00Z");
    }

    private DataSource reachableDataSource() {
        try {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(1)).thenReturn(true);
            return dataSource;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Flyway flyway(String version) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        if (version == null) {
            when(info.current()).thenReturn(null);
        } else {
            MigrationInfo migration = mock(MigrationInfo.class);
            when(info.current()).thenReturn(migration);
            when(migration.getVersion()).thenReturn(org.flywaydb.core.api.MigrationVersion.fromVersion(version));
        }
        return flyway;
    }
}
