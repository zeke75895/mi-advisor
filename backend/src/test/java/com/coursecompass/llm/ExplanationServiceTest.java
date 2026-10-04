package com.coursecompass.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coursecompass.llm.ExplanationService.Explanation;
import com.coursecompass.llm.ExplanationService.ExplanationInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExplanationServiceTest {

    private static final ExplanationInput INPUT = new ExplanationInput(
            "lean_withdraw",
            "Leaning toward withdraw, but it's close",
            "high",
            58,
            67L,
            35,
            "88% average on the remaining work",
            1,
            2,
            List.of(
                    "The prediction model rates this course as high risk based on your study patterns.",
                    "Your projected final grade is 58, below the 70 needed for a C.",
                    "35% of your grade is still ahead, so recovery is possible."),
            List.of("Meet with your professor to discuss recovery options"));

    private GeminiClient gemini;
    private ExplanationService service;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiClient.class);
        when(gemini.isEnabled()).thenReturn(true);
        service = new ExplanationService(gemini, new AiRateLimiter(100, Duration.ofMinutes(10), 100, Clock.systemUTC()), new ObjectMapper());
    }

    @Test
    void usesGeminiRewriteWhenItPassesChecks() {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn("""
                {"summary": "Your projected grade of 58 is below a C, and our prediction sees high risk. \
                A good next step is to meet with your professor about recovery options.",
                 "reasoning": ["Our prediction sees this course as high risk for you right now.",
                               "You're currently projected at 58, and a C needs 70.",
                               "With 35% of your grade still ahead, there's real room to recover."]}
                """);

        Explanation e = service.explain(INPUT);

        assertThat(e.source()).isEqualTo("gemini");
        assertThat(e.label()).isEqualTo("AI-generated");
        assertThat(e.reasoning()).hasSize(3).first().asString().startsWith("Our prediction");
        assertThat(e.summary()).contains("professor");
    }

    @Test
    void promptCarriesSignalsAndBulletsButNoPersonalData() {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn("{}");
        service.explain(INPUT);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(system.capture(), user.capture(), any());
        assertThat(system.getValue()).contains("Do not add any number");
        assertThat(user.getValue())
                .contains("Recommendation: lean_withdraw")
                .contains("Projected final grade: 58")
                .contains("- Your projected final grade is 58, below the 70 needed for a C.")
                .contains("1 of 2 rated")
                .doesNotContain("{{")
                .doesNotContain("@");
    }

    @Test
    void fallsBackWhenGeminiInventsANumber() {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn("""
                {"summary": "About 80% of students like you recover. Talk to your professor.",
                 "reasoning": ["a", "b", "c"]}
                """);

        Explanation e = service.explain(INPUT);

        assertThat(e.source()).isEqualTo("template");
        assertThat(e.label()).isNull();
        assertThat(e.reasoning()).isEqualTo(INPUT.reasoning());
    }

    @Test
    void fallsBackWhenBulletCountDiffers() {
        when(gemini.generateJson(anyString(), anyString(), any()))
                .thenReturn("{\"summary\": \"Short.\", \"reasoning\": [\"only one\"]}");

        assertThat(service.explain(INPUT).source()).isEqualTo("template");
    }

    @Test
    void fallsBackOnInvalidJsonOrClientError() {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn("not json");
        assertThat(service.explain(INPUT).source()).isEqualTo("template");

        when(gemini.generateJson(anyString(), anyString(), any())).thenThrow(new GeminiException("timeout"));
        assertThat(service.explain(INPUT).source()).isEqualTo("template");
    }

    @Test
    void usesTemplateWhenGlobalAiLimitIsReached() {
        ExplanationService limited = new ExplanationService(
                gemini, new AiRateLimiter(100, Duration.ofMinutes(10), 0, Clock.systemUTC()), new ObjectMapper());

        assertThat(limited.explain(INPUT).source()).isEqualTo("template");
        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    void skipsGeminiWhenNoApiKey() {
        when(gemini.isEnabled()).thenReturn(false);

        assertThat(service.explain(INPUT).source()).isEqualTo("template");
        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    void cachesSuccessfulRewritesForIdenticalSignals() {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn("""
                {"summary": "You can do this. Meet with your professor.",
                 "reasoning": ["One.", "Two.", "Three."]}
                """);

        service.explain(INPUT);
        service.explain(INPUT);

        verify(gemini, times(1)).generateJson(anyString(), anyString(), any());
    }
}
