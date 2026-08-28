package com.oj.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerHealthControllerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void livenessStaysUpAndReadinessRequiresDatabaseListenerAndRunner() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/readiness", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);
        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listeners,
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThat(controller.liveness()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.readiness().getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
    }

    @Test
    void readinessIsDownWhenRunnerIsUnavailableWithoutChangingLiveness() throws Exception {
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);
        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listeners,
                "http://127.0.0.1:1");

        assertThat(controller.liveness()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessIsDownWhenRabbitListenerIsUnavailable() throws Exception {
        RabbitListenerEndpointRegistry listenerDown = mock(RabbitListenerEndpointRegistry.class);
        when(listenerDown.isRunning()).thenReturn(false);
        WorkerHealthController listenerUnavailable = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listenerDown,
                "http://127.0.0.1:1");

        assertThat(listenerUnavailable.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessFailsClosedWithinBudgetWhenDatabaseAcquisitionBlocks() throws Exception {
        DataSource blockingDataSource = mock(DataSource.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(blockingDataSource.getConnection()).thenAnswer(invocation -> {
            entered.countDown();
            Thread.sleep(5_000);
            throw new SQLException("unreachable");
        });
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);

        try (var probe = new BoundedReadinessProbe(Duration.ofMillis(150))) {
            WorkerHealthController controller = new WorkerHealthController(blockingDataSource, probe, listeners,
                    "http://127.0.0.1:1");
            long startedAt = System.nanoTime();
            assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(elapsedMs).isLessThan(750);
        }
    }

    @Test
    void readinessFailsClosedWithinBudgetWhenRunnerReadinessBlocks() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/readiness", exchange -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);
        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listeners,
                "http://127.0.0.1:" + server.getAddress().getPort());

        long startedAt = System.nanoTime();
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(elapsedMs).isLessThan(1_500);
    }

    private DataSource reachableDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        return dataSource;
    }
}
