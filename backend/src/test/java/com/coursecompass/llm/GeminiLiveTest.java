package com.coursecompass.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.coursecompass.llm.ExplanationService.Explanation;
import com.coursecompass.llm.ExplanationService.ExplanationInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Makes one real Gemini call. Skipped unless GEMINI_API_KEY is set, so CI and offline runs don't need a
 * key or spend free-tier quota. Run with: set -a; source ../.env; set +a; mvn test -Dtest=GeminiLiveTest
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiLiveTest {

    @Test
    void realGeminiRewritePassesTheSafetyChecks() {
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite");
        GeminiClient client = new GeminiClient(
                RestClient.builder(),
                "https://generativelanguage.googleapis.com/v1beta",
                System.getenv("GEMINI_API_KEY"),
                model,
                Duration.ofSeconds(30));
        ExplanationService service = new ExplanationService(client, new ObjectMapper());

        Explanation e = service.explain(new ExplanationInput(
                "lean_withdraw",
                "Leaning toward withdraw, but it's close",
                "high",
                52,
                61L,
                35,
                2,
                2,
                List.of(
                        "The prediction model rates this course as high risk based on your study patterns.",
                        "Your projected final grade is 52, below the 70 needed for a C.",
                        "35% of your grade is still ahead, so recovery is possible."),
                List.of(
                        "Meet with your professor to discuss recovery options",
                        "Calculate what you'd need on remaining work to pass",
                        "Talk to your advisor before the drop deadline")));

        System.out.println("Gemini (" + model + ") summary: " + e.summary());
        e.reasoning().forEach(b -> System.out.println("  - " + b));
        assertThat(e.source()).as("rewrite should pass the shape and number checks").isEqualTo("gemini");
        assertThat(e.label()).isEqualTo("AI-generated");
        assertThat(e.reasoning()).hasSize(3);
    }
}
