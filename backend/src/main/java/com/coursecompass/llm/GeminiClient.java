package com.coursecompass.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Minimal client for Gemini's generateContent REST endpoint, returning structured JSON output. */
@Component
public class GeminiClient {

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
        if (!isEnabled()) {
            throw new GeminiException("GEMINI_API_KEY is not set");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "maxOutputTokens", 800,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));
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
        } catch (RestClientException e) {
            throw new GeminiException("Gemini request failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
