package com.oj.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;

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
        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), listeners,
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThat(controller.liveness()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.readiness().getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
    }

    @Test
    void readinessIsDownWhenRunnerIsUnavailableWithoutChangingLiveness() throws Exception {
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        when(listeners.isRunning()).thenReturn(true);
        WorkerHealthController controller = new WorkerHealthController(reachableDataSource(), listeners,
                "http://127.0.0.1:1");

        assertThat(controller.liveness()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
        assertThat(controller.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessIsDownWhenDatabaseOrRabbitListenerIsUnavailable() throws Exception {
        RabbitListenerEndpointRegistry listenerDown = mock(RabbitListenerEndpointRegistry.class);
        when(listenerDown.isRunning()).thenReturn(false);
        WorkerHealthController noDatabase = new WorkerHealthController(mock(DataSource.class), listenerDown,
                "http://127.0.0.1:1");

        assertThat(noDatabase.readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private DataSource reachableDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        return dataSource;
    }
}
