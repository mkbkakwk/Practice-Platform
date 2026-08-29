package com.oj.sandbox.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.judge.JudgeService;
import com.oj.sandbox.SandboxCaseResult;
import com.oj.sandbox.SandboxClientException;
import com.oj.sandbox.SandboxCompileResult;
import com.oj.sandbox.SandboxLanguage;
import com.oj.sandbox.SandboxLimits;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;
import com.oj.sandbox.SandboxTestCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteSandboxClientTest {

    private static final String TOKEN = "test-runner-token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsVersionedRequestWithBearerTokenAndParsesSuccess() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> correlation = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        SandboxRequest request = request();
        start(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            correlation.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, objectMapper.writeValueAsBytes(success(request, "1")));
        });

        SandboxResult result = client(1_000, 65_536, 65_536).execute(request);

        assertThat(result.requestId()).isEqualTo(request.requestId());
        assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(correlation.get()).isEqualTo(request.requestId());
        assertThat(requestBody.get().get("language").asText()).isEqualTo("PYTHON");
        assertThat(requestBody.get().has("command")).isFalse();
        assertThat(requestBody.get().has("compileCommand")).isFalse();
        assertThat(requestBody.get().toString()).doesNotContain("expectedOutput");
    }

    @Test
    void http500FailsClosed() throws Exception {
        start(exchange -> respond(exchange, 500, "failure".getBytes(StandardCharsets.UTF_8)));
        JudgeService service = new JudgeService(client(1_000, 65_536, 65_536), 10_000, 65_536);

        JudgeService.JudgeResult result = service.judge(
                "python", "print(1)", 1_000, 262_144,
                "[{\"input\":\"\",\"output\":\"1\"}]");

        assertThat(result.verdict).isEqualTo("SE");
    }

    @Test
    void readTimeoutFailsClosed() throws Exception {
        start(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(300);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        assertThatThrownBy(() -> client(50, 65_536, 65_536).execute(request()))
                .isInstanceOf(SandboxClientException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void malformedJsonFailsClosed() throws Exception {
        start(exchange -> respond(exchange, 200, "{not-json".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client(1_000, 65_536, 65_536).execute(request()))
                .isInstanceOf(SandboxClientException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void unknownStatusFailsClosed() throws Exception {
        SandboxRequest request = request();
        String body = """
                {"requestId":"%s","compile":{"status":"UNKNOWN","exitCode":0,
                "stderr":"","timeMs":1,"message":""},"cases":[],"message":""}
                """.formatted(request.requestId());
        start(exchange -> respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client(1_000, 65_536, 65_536).execute(request))
                .isInstanceOf(SandboxClientException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void requestIdMismatchFailsClosed() throws Exception {
        start(exchange -> {
            SandboxResult response = success(request(), "1");
            response = new SandboxResult(UUID.randomUUID().toString(), response.compile(), response.cases(), "");
            respond(exchange, 200, objectMapper.writeValueAsBytes(response));
        });

        assertThatThrownBy(() -> client(1_000, 65_536, 65_536).execute(request()))
                .isInstanceOf(SandboxClientException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    void oversizedHttpResponseFailsBeforeJsonParsing() throws Exception {
        start(exchange -> respond(exchange, 200, "x".repeat(1_024).getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client(1_000, 65_536, 256).execute(request()))
                .isInstanceOf(SandboxClientException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void oversizedCaseOutputFailsProtocolValidation() throws Exception {
        SandboxRequest request = request(32);
        start(exchange -> respond(
                exchange, 200, objectMapper.writeValueAsBytes(success(request, "x".repeat(33)))));

        assertThatThrownBy(() -> client(1_000, 65_536, 65_536).execute(request))
                .isInstanceOf(SandboxClientException.class)
                .hasMessageContaining("case output exceeds");
    }

    private RemoteSandboxClient client(long readTimeoutMs, int maxRequestBytes, int maxResponseBytes) {
        return new RemoteSandboxClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                TOKEN,
                1_000,
                readTimeoutMs,
                65_536,
                65_536,
                maxRequestBytes,
                maxResponseBytes,
                objectMapper);
    }

    private SandboxRequest request() {
        return request(65_536);
    }

    private SandboxRequest request(int outputLimitBytes) {
        return new SandboxRequest(
                UUID.randomUUID().toString(), SandboxLanguage.PYTHON, "print(1)",
                new SandboxLimits(10_000, 1_000, 256, outputLimitBytes),
                List.of(new SandboxTestCase("1", "")));
    }

    private SandboxResult success(SandboxRequest request, String stdout) {
        return new SandboxResult(
                request.requestId(),
                new SandboxCompileResult(SandboxStatus.OK, 0, "", 2, ""),
                List.of(new SandboxCaseResult("1", SandboxStatus.OK, 0, stdout, "", 5, 1024, "")),
                "");
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(RemoteSandboxClient.API_PATH, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
