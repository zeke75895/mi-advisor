package com.miadvisor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Minimal client for Gemini's generateContent REST endpoint, returning structured JSON output. */
@Component
public class GeminiClient {

    static final int DEFAULT_MAX_OUTPUT_TOKENS = 800;
    static final int MAX_ATTEMPTS = 2;
    static final Duration RETRY_DELAY = Duration.ofMillis(800);

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            RestClient.Builder builder,
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model}") String model,
            @Value("${gemini.timeout}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Sends a system + user prompt and returns the model's JSON text, constrained by responseSchema.
     *
     * @param responseSchema Gemini OpenAPI-style schema (types in upper case, e.g. "OBJECT")
     */
    public String generateJson(String systemPrompt, String userPrompt, Map<String, Object> responseSchema) {
        return generateJson(systemPrompt, userPrompt, responseSchema, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    /** Newer Gemini models spend part of maxOutputTokens on internal reasoning, so leave headroom. */
    public String generateJson(
            String systemPrompt, String userPrompt, Map<String, Object> responseSchema, int maxOutputTokens) {
        if (!isEnabled()) {
            throw new GeminiNotConfiguredException();
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "maxOutputTokens", maxOutputTokens,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri("/models/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey) // header, not ?key=, so the key never lands in URL logs
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode text = response == null ? null : response.at("/candidates/0/content/parts/0/text");
                if (text == null || text.isMissingNode() || text.asText().isBlank()) {
                    throw new GeminiException("Gemini returned no text (blocked or empty response)");
                }
                return text.asText();
            } catch (HttpClientErrorException e) {
                lastError = e; // 4xx: the request itself is wrong (or quota is spent); retrying won't help
                break;
            } catch (RestClientException e) {
                lastError = e; // 5xx or network trouble: worth one more try
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_DELAY);
                }
            }
        }
        // The API key travels in a header, so the message (status + response body) never contains it
        String detail = lastError.getMessage() == null ? "" : lastError.getMessage();
        throw new GeminiException("Gemini request failed: " + lastError.getClass().getSimpleName() + ": "
                + detail.substring(0, Math.min(300, detail.length())), lastError);
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
