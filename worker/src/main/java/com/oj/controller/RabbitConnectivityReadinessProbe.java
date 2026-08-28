package com.oj.controller;

import com.rabbitmq.client.Channel;
import jakarta.annotation.PreDestroy;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Confirms that the Worker can currently use its existing AMQP connection
 * infrastructure. It never publishes, consumes, or changes Rabbit topology.
 */
@Component
public class RabbitConnectivityReadinessProbe implements AutoCloseable {
    private final ConnectionFactory connectionFactory;
    private final String queueName;
    private final BoundedReadinessProbe boundedProbe = new BoundedReadinessProbe();

    public RabbitConnectivityReadinessProbe(ConnectionFactory connectionFactory,
                                            @Value("${oj.rabbitmq.queue:oj.judge.queue}") String queueName) {
        this.connectionFactory = connectionFactory;
        this.queueName = queueName;
    }

    public boolean brokerReachable() {
        return boundedProbe.check(this::canCreateChannel);
    }

    private boolean canCreateChannel() {
        Connection connection = null;
        Channel channel = null;
        try {
            connection = connectionFactory.createConnection();
            if (!connection.isOpen()) return false;
            channel = connection.createChannel(false);
            if (channel == null || !channel.isOpen()) return false;
            // Passive declaration is an AMQP round trip that only verifies an
            // existing queue. It never creates, deletes, publishes, consumes,
            // or otherwise changes application topology.
            channel.queueDeclarePassive(queueName);
            return channel.isOpen();
        } catch (Exception ignored) {
            return false;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Exception ignored) {
                    // Readiness must never surface AMQP implementation details.
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // CachingConnectionFactory safely releases the shared connection proxy.
                }
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        boundedProbe.close();
    }
}
