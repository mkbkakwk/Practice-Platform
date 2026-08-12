package com.oj.runner.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "runner.sandbox", name = "mode", havingValue = "docker")
public class DockerClientConfiguration {

    @Bean(destroyMethod = "close")
    DockerHttpClient dockerHttpClient(DockerSandboxProperties properties) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getHost())
                .build();
        return new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(32)
                .connectionTimeout(Duration.ofMillis(properties.getControlTimeoutMs()))
                .responseTimeout(Duration.ofMillis(properties.getControlTimeoutMs()))
                .build();
    }

    @Bean(destroyMethod = "close")
    DockerClient dockerClient(DockerSandboxProperties properties, DockerHttpClient httpClient) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getHost())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
