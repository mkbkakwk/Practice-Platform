package com.oj.sandbox.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.sandbox.SandboxCaseResult;
import com.oj.sandbox.SandboxClient;
import com.oj.sandbox.SandboxClientException;
import com.oj.sandbox.SandboxCompileResult;
import com.oj.sandbox.SandboxRequest;
import com.oj.sandbox.SandboxRequestValidator;
import com.oj.sandbox.SandboxResult;
import com.oj.sandbox.SandboxStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** HTTP client for the versioned, private Sandbox Runner execution API. */
public final class RemoteSandboxClient implements SandboxClient {

    public static final String API_PATH = "/api/v1/jobs";

    private final URI endpoint;
    private final String token;
    private final long readTimeoutMs;
    private final int maxSourceBytes;
    private final int maxStdinBytes;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RemoteSandboxClient(
            String baseUrl,
            String token,
            long connectTimeoutMs,
            long readTimeoutMs,
            int maxSourceBytes,
            int maxStdinBytes,
            int maxRequestBytes,
            int maxResponseBytes,
            ObjectMapper objectMapper) {
        this(baseUrl, token, readTimeoutMs, maxSourceBytes, maxStdinBytes,
                maxRequestBytes, maxResponseBytes, objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(requirePositive(connectTimeoutMs, "connect timeout")))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    RemoteSandboxClient(
            String baseUrl,
            String token,
            long readTimeoutMs,
            int maxSourceBytes,
            int maxStdinBytes,
            int maxRequestBytes,
            int maxResponseBytes,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.endpoint = endpoint(baseUrl);
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            throw new IllegalArgumentException("Runner token must be non-empty and contain no line breaks");
        }
        this.token = token;
        this.readTimeoutMs = requirePositive(readTimeoutMs, "read timeout");
        this.maxSourceBytes = requirePositive(maxSourceBytes, "source limit");
        this.maxStdinBytes = requirePositive(maxStdinBytes, "stdin limit");
        this.maxRequestBytes = requirePositive(maxRequestBytes, "request limit");
        this.maxResponseBytes = requirePositive(maxResponseBytes, "response limit");
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        SandboxRequestValidator.validate(request, maxSourceBytes, maxStdinBytes);
        byte[] body = serialize(request);
        if (body.length > maxRequestBytes) {
            throw new SandboxClientException("Runner request exceeds the configured limit");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        CompletableFuture<HttpResponse<byte[]>> responseFuture = httpClient.sendAsync(
                httpRequest, responseInfo -> new BoundedBodySubscriber(maxResponseBytes));
        try {
            HttpResponse<byte[]> response = responseFuture.get(readTimeoutMs, TimeUnit.MILLISECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SandboxClientException("Runner returned HTTP " + response.statusCode());
            }
            SandboxResult result = deserialize(response.body());
            validateResponse(request, result);
            return result;
        } catch (TimeoutException exception) {
            responseFuture.cancel(true);
            throw new SandboxClientException("Runner request timed out", exception);
        } catch (InterruptedException exception) {
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw new SandboxClientException("Runner request was interrupted", exception);
        } catch (ExecutionException exception) {
            if (hasCause(exception, HttpTimeoutException.class)) {
                throw new SandboxClientException("Runner request timed out", exception);
            }
            if (hasCause(exception, ResponseTooLargeException.class)) {
                throw new SandboxClientException("Runner response exceeds the configured limit", exception);
            }
            if (hasCause(exception, IOException.class)) {
                throw new SandboxClientException("Runner connection failed", exception);
            }
            throw new SandboxClientException("Runner request failed", exception);
        }
    }

    private byte[] serialize(SandboxRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new SandboxClientException("Runner request could not be encoded", exception);
        }
    }

    private SandboxResult deserialize(byte[] body) {
        try {
            return objectMapper.readValue(body, SandboxResult.class);
        } catch (IOException exception) {
            throw new SandboxClientException("Runner returned an invalid response", exception);
        }
    }

    private void validateResponse(SandboxRequest request, SandboxResult result) {
        if (result == null || !request.requestId().equals(result.requestId()) || result.message() == null) {
            throw new SandboxClientException("Runner response requestId does not match the request");
        }
        SandboxCompileResult compile = result.compile();
        if (compile == null || compile.status() == null || compile.exitCode() == null || compile.timeMs() < 0
                || compile.stderr() == null || compile.message() == null) {
            throw new SandboxClientException("Runner response contains an invalid compile result");
        }
        if (SandboxRequestValidator.utf8Length(compile.stderr()) > request.limits().outputLimitBytes()) {
            throw new SandboxClientException("Runner compile output exceeds the configured limit");
        }
        if (compile.status() == SandboxStatus.OK && compile.exitCode() != 0) {
            throw new SandboxClientException("Runner returned a successful compile with a non-zero exit code");
        }
        List<SandboxCaseResult> cases = result.cases();
        if (cases == null) {
            throw new SandboxClientException("Runner response cases are required");
        }
        if (compile.status() != SandboxStatus.OK) {
            if (!cases.isEmpty()) {
                throw new SandboxClientException("Runner returned cases after compilation failed");
            }
            return;
        }
        if (cases.isEmpty() || cases.size() > request.cases().size()) {
            throw new SandboxClientException("Runner response contains an invalid case count");
        }

        Set<String> seenCaseIds = new HashSet<>();
        for (int index = 0; index < cases.size(); index++) {
            SandboxCaseResult caseResult = cases.get(index);
            String expectedCaseId = request.cases().get(index).caseId();
            if (caseResult == null || caseResult.status() == null
                    || !expectedCaseId.equals(caseResult.caseId())
                    || !seenCaseIds.add(caseResult.caseId())
                    || caseResult.exitCode() == null || caseResult.stdout() == null || caseResult.stderr() == null
                    || caseResult.message() == null || caseResult.timeMs() < 0 || caseResult.memoryKb() < 0) {
                throw new SandboxClientException("Runner response contains an invalid case result");
            }
            int combinedOutput = SandboxRequestValidator.utf8Length(caseResult.stdout())
                    + SandboxRequestValidator.utf8Length(caseResult.stderr());
            if (combinedOutput > request.limits().outputLimitBytes()) {
                throw new SandboxClientException("Runner case output exceeds the configured limit");
            }
            if (caseResult.status() == SandboxStatus.OK && caseResult.exitCode() != 0) {
                throw new SandboxClientException("Runner returned OK with a non-zero exit code");
            }
            if (index < cases.size() - 1 && caseResult.status() != SandboxStatus.OK) {
                throw new SandboxClientException("Runner returned results after a failed case");
            }
        }
        SandboxCaseResult last = cases.getLast();
        if (cases.size() < request.cases().size() && last.status() == SandboxStatus.OK) {
            throw new SandboxClientException("Runner response omitted successful cases");
        }
    }

    private static URI endpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Runner base URL is required");
        }
        URI base = URI.create(baseUrl.trim());
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("Runner base URL must be an HTTP(S) origin or path");
        }
        String normalized = base.toString().replaceAll("/+$", "");
        return URI.create(normalized + API_PATH);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("Runner " + name + " must be positive");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("Runner " + name + " must be positive");
        }
        return value;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Bounds bytes while the HTTP client is still enforcing the request timeout. */
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private BoundedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int count = buffer.remaining();
                if (count > maxBytes - captured.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[count];
                buffer.get(bytes);
                captured.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(captured.toByteArray());
        }
    }

    private static final class ResponseTooLargeException extends RuntimeException {
    }
}
