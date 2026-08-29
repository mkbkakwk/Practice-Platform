package com.oj.observability;

import com.oj.config.AppProperties;
import com.oj.reliability.JudgeOutboxRepository;
import com.oj.reliability.OutboxPublisherStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void returnsBoundedSafePartialSnapshotWhenDependenciesAreUnavailable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(rabbit.execute(any())).thenThrow(new IllegalStateException("broker unavailable"));
        JudgeOutboxRepository outbox = mock(JudgeOutboxRepository.class);
        OutboxPublisherStatus publisher = new OutboxPublisherStatus();

        try (BoundedStatusProbe probe = new BoundedStatusProbe()) {
            SystemStatusService service = new SystemStatusService(dataSource, mock(Flyway.class), rabbit,
                    new AppProperties(), outbox, publisher, new OperationalMetrics(), probe,
                    "http://127.0.0.1:1/api/readiness", "http://127.0.0.1:1/api/readiness",
                    "0123456789abcdef0123456789abcdef01234567", "test", "2026-08-29T00:00:00Z");
            Map<String, Object> snapshot = service.snapshot();

            Map<String, Object> components = (Map<String, Object>) snapshot.get("components");
            assertEquals("DOWN", ((Map<String, Object>) components.get("rabbitmq")).get("status"));
            assertEquals("DOWN", ((Map<String, Object>) components.get("worker")).get("status"));
            assertEquals("UNKNOWN", ((Map<String, Object>) snapshot.get("queues")).get("main"));
            assertFalse(snapshot.toString().contains("password"));
            assertTrue(snapshot.containsKey("metrics"));
        }
    }
}
