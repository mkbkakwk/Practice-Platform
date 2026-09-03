package com.oj.controller;

import com.rabbitmq.client.Channel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
                reachableRabbitProbe(),
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
                reachableRabbitProbe(),
                "http://127.0.0.1:1");

        assertThat(controller.liveness()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessIsDownWhenRabbitListenerIsUnavailable() throws Exception {
        RabbitListenerEndpointRegistry listenerDown = mock(RabbitListenerEndpointRegistry.class);
        when(listenerDown.isRunning()).thenReturn(false);
        WorkerHealthController listenerUnavailable = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listenerDown,
                reachableRabbitProbe(),
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
                    reachableRabbitProbe(),
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
                reachableRabbitProbe(),
                "http://127.0.0.1:" + server.getAddress().getPort());

        long startedAt = System.nanoTime();
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(elapsedMs).isLessThan(1_500);
    }

    @Test
    void readinessIsDownWhenListenerRunsButBrokerIsUnavailable() throws Exception {
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);
        ConnectionFactory unavailableFactory = mock(ConnectionFactory.class);
        when(unavailableFactory.createConnection()).thenThrow(new IllegalStateException("broker unavailable"));

        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listeners,
                new RabbitConnectivityReadinessProbe(unavailableFactory, "test.queue"), "http://127.0.0.1:1");

        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessFailsClosedWithinBudgetWhenBrokerProbeBlocks() throws Exception {
        ConnectionFactory blockingFactory = mock(ConnectionFactory.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(blockingFactory.createConnection()).thenAnswer(invocation -> {
            entered.countDown();
            Thread.sleep(5_000);
            throw new IllegalStateException("broker unavailable");
        });
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);

        try (var rabbitProbe = new RabbitConnectivityReadinessProbe(blockingFactory, "test.queue")) {
            WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listeners,
                    rabbitProbe, "http://127.0.0.1:1");
            long startedAt = System.nanoTime();
            assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(elapsedMs).isLessThan(1_500);
        }
    }

    @Test
    void readinessRecoversWhenBrokerBecomesReachableWithoutRecreatingWorker() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/readiness", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        AtomicBoolean brokerAvailable = new AtomicBoolean(false);
        ConnectionFactory connectionFactory = reachableRabbitConnectionFactory();
        org.springframework.amqp.rabbit.connection.Connection connection = connectionFactory.createConnection();
        when(connectionFactory.createConnection()).thenAnswer(invocation -> {
            if (!brokerAvailable.get()) throw new IllegalStateException("broker unavailable");
            return connection;
        });
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);

        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), new BoundedReadinessProbe(), listeners,
                new RabbitConnectivityReadinessProbe(connectionFactory, "test.queue"),
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        brokerAvailable.set(true);
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private DataSource reachableDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        return dataSource;
    }

    private RabbitConnectivityReadinessProbe reachableRabbitProbe() {
        return new RabbitConnectivityReadinessProbe(reachableRabbitConnectionFactory(), "test.queue");
    }

    private ConnectionFactory reachableRabbitConnectionFactory() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        org.springframework.amqp.rabbit.connection.Connection connection = mock(org.springframework.amqp.rabbit.connection.Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel(false)).thenReturn(channel);
        when(channel.isOpen()).thenReturn(true);
        return connectionFactory;
    }
}
