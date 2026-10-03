package com.coursecompass.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Rewrites the fusion reasoning bullets into supportive plain language with Gemini.
 *
 * <p>Only aggregate signals are sent (no email, name or course details). The LLM output is checked
 * before use: it must return one bullet per input bullet and may not introduce any number that isn't in
 * the prompt. On any failure, or when no API key is configured, the original template bullets are used.
 */
@Service
public class ExplanationService {

    public static final String SOURCE_GEMINI = "gemini";
    public static final String SOURCE_TEMPLATE = "template";
    public static final String AI_LABEL = "AI-generated";

    private static final Logger log = LoggerFactory.getLogger(ExplanationService.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "summary", Map.of("type", "STRING"),
                    "reasoning", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))),
            "required", List.of("summary", "reasoning"));

    private final GeminiClient gemini;
    private final ObjectMapper json;
    private final PromptTemplate systemPrompt = PromptTemplate.load("recommendation-explanation.system.txt");
    private final PromptTemplate userPrompt = PromptTemplate.load("recommendation-explanation.user.txt");
    // Same signals -> same prompt -> reuse the rewrite instead of spending Gemini quota on every page load
    private final Map<String, Explanation> cache = new ConcurrentHashMap<>();

    public ExplanationService(GeminiClient gemini, ObjectMapper json) {
        this.gemini = gemini;
        this.json = json;
    }

    /** Raw signals the prompt is built from. Numbers are pre-rounded to what the student should see. */
    public record ExplanationInput(
            String recommendation,
            String headline,
            String riskLevel,
            long projectedFinal,
            Long currentGrade,
            long remainingPct,
            int lowRatings,
            int ratedItems,
            List<String> reasoning,
            List<String> actions) {}

    /** @param label "AI-generated" when Gemini wrote it, otherwise null */
    public record Explanation(String source, String label, String summary, List<String> reasoning) {}

    public Explanation explain(ExplanationInput input) {
        Explanation fallback = new Explanation(SOURCE_TEMPLATE, null, null, input.reasoning());
        if (!gemini.isEnabled()) {
            return fallback;
        }
        String prompt = renderUserPrompt(input);
        Explanation cached = cache.get(prompt);
        if (cached != null) {
            return cached;
        }
        try {
            String raw = gemini.generateJson(systemPrompt.text(), prompt, RESPONSE_SCHEMA);
            Explanation explanation = parseAndCheck(raw, input, prompt);
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(prompt, explanation);
            return explanation;
        } catch (RuntimeException e) {
            log.warn("Using template explanation: {}", e.getMessage());
            return fallback;
        }
    }

    String renderUserPrompt(ExplanationInput in) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recommendation", in.recommendation());
        values.put("headline", in.headline());
        values.put("risk_level", in.riskLevel());
        values.put("projected_final", Long.toString(in.projectedFinal()));
        values.put("current_grade", in.currentGrade() == null ? "nothing graded yet" : in.currentGrade().toString());
        values.put("remaining_pct", Long.toString(in.remainingPct()));
        values.put("low_ratings", Integer.toString(in.lowRatings()));
        values.put("rated_items", Integer.toString(in.ratedItems()));
        values.put("reasoning_bullets", bullets(in.reasoning()));
        values.put("actions", bullets(in.actions()));
        return userPrompt.render(values);
    }

    private Explanation parseAndCheck(String raw, ExplanationInput input, String prompt) {
        JsonNode node;
        try {
            node = json.readTree(raw);
        } catch (Exception e) {
            throw new GeminiException("Gemini returned invalid JSON", e);
        }
        String summary = node.path("summary").asText("").strip();
        List<String> reasoning = new ArrayList<>();
        node.path("reasoning").forEach(b -> reasoning.add(b.asText("").strip()));

        if (summary.isEmpty() || reasoning.size() != input.reasoning().size() || reasoning.contains("")) {
            throw new GeminiException("Gemini output has the wrong shape");
        }
        Set<String> allowed = numbers(prompt);
        Set<String> invented = numbers(summary + " " + String.join(" ", reasoning));
        invented.removeAll(allowed);
        if (!invented.isEmpty()) {
            throw new GeminiException("Gemini introduced numbers not in the input: " + invented);
        }
        return new Explanation(SOURCE_GEMINI, AI_LABEL, summary, List.copyOf(reasoning));
    }

    private static Set<String> numbers(String text) {
        Set<String> found = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    private static String bullets(List<String> lines) {
        return lines.stream().map(line -> "- " + line).collect(Collectors.joining("\n"));
    }
}
