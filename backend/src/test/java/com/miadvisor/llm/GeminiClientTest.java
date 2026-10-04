package com.miadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Checks the wire format against a local stand-in for the Gemini REST API. */
class GeminiClientTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile int failuresBeforeSuccess = 0;
    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    private volatile String response = "{}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean fail = calls.incrementAndGet() <= failuresBeforeSuccess;
            byte[] bytes = (fail ? "{\"error\": \"overloaded\"}" : response).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(fail ? 503 : status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiClient client(String apiKey) {
        return new GeminiClient(
                RestClient.builder(),
                "http://localhost:" + server.getAddress().getPort() + "/v1beta",
                apiKey,
                "gemini-test",
                Duration.ofSeconds(2));
    }

    @Test
    void sendsGenerateContentRequestAndReturnsText() throws Exception {
        response = """
                {"candidates": [{"content": {"parts": [{"text": "{\\"summary\\": \\"hi\\"}"}]}}]}
                """;

        String text = client("secret-key").generateJson("SYSTEM", "USER", Map.of("type", "OBJECT"));

        assertThat(text).isEqualTo("{\"summary\": \"hi\"}");
        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-test:generateContent");
        assertThat(apiKeyHeader.get()).isEqualTo("secret-key");
        JsonNode sent = new ObjectMapper().readTree(body.get());
        assertThat(sent.at("/systemInstruction/parts/0/text").asText()).isEqualTo("SYSTEM");
        assertThat(sent.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(sent.at("/contents/0/parts/0/text").asText()).isEqualTo("USER");
        assertThat(sent.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        assertThat(sent.at("/generationConfig/responseSchema/type").asText()).isEqualTo("OBJECT");
    }

    @Test
    void emptyCandidatesIsAnError() {
        response = "{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}";

        assertThatThrownBy(() -> client("k").generateJson("s", "u", Map.of()))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("no text");
    }

    @Test
    void httpErrorIsWrappedWithoutLeakingTheKey() {
        status = 429;
        response = "{\"error\": {\"message\": \"quota\"}}";

        assertThatThrownBy(() -> client("secret-key").generateJson("s", "u", Map.of()))
                .isInstanceOf(GeminiException.class)
                .hasMessageNotContaining("secret-key");
    }

    @Test
    void retriesOnceAfterAServerError() {
        failuresBeforeSuccess = 1;
        response = """
                {"candidates": [{"content": {"parts": [{"text": "ok"}]}}]}
                """;

        assertThat(client("k").generateJson("s", "u", Map.of())).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void givesUpAfterTwoServerErrorsAndNeverRetriesClientErrors() {
        failuresBeforeSuccess = 5;
        assertThatThrownBy(() -> client("k").generateJson("s", "u", Map.of())).isInstanceOf(GeminiException.class);
        assertThat(calls.get()).isEqualTo(2);

        calls.set(0);
        failuresBeforeSuccess = 0;
        status = 400;
        assertThatThrownBy(() -> client("k").generateJson("s", "u", Map.of())).isInstanceOf(GeminiException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void disabledWithoutKey() {
        assertThat(client("").isEnabled()).isFalse();
        assertThatThrownBy(() -> client("").generateJson("s", "u", Map.of())).isInstanceOf(GeminiException.class);
    }
}
