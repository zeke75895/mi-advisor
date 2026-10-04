package com.miadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miadvisor.course.Course;
import com.miadvisor.course.CourseService;
import com.miadvisor.llm.ExplanationService.Explanation;
import com.miadvisor.llm.ExplanationService.ExplanationInput;
import com.miadvisor.study.StudyDtos.FlashcardResponse;
import com.miadvisor.study.StudyDtos.QuestionResponse;
import com.miadvisor.study.StudyMaterialService;
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

    private static final String MODEL = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite");
    private static final String NOTES = "Photosynthesis converts light energy into chemical energy stored in glucose."
            + " It happens in chloroplasts, which contain chlorophyll. The light-dependent reactions occur in the"
            + " thylakoid membranes, split water, release oxygen and make ATP and NADPH. The Calvin cycle runs in the"
            + " stroma, where RuBisCO fixes carbon dioxide using ATP and NADPH. Light intensity, carbon dioxide"
            + " concentration and temperature limit the rate. C4 and CAM plants reduce photorespiration.";

    private static GeminiClient client() {
        return new GeminiClient(
                RestClient.builder(),
                "https://generativelanguage.googleapis.com/v1beta",
                System.getenv("GEMINI_API_KEY"),
                MODEL,
                Duration.ofSeconds(60));
    }

    private static StudyMaterialService materialService() {
        CourseService courses = mock(CourseService.class);
        when(courses.getOwned(1L, 1L)).thenReturn(new Course(null, "BIO 181", "Intro Biology", 4, null));
        return new StudyMaterialService(
                courses,
                client(),
                limiter(),
                mock(com.miadvisor.notes.NotesService.class),
                mock(com.miadvisor.study.GeneratedMaterialRepository.class),
                new ObjectMapper().findAndRegisterModules(),
                java.time.Clock.systemUTC());
    }

    private static AiRateLimiter limiter() {
        return new AiRateLimiter(100, Duration.ofMinutes(10), 100, java.time.Clock.systemUTC());
    }

    @Test
    void realGeminiMakesTenFlashcards() {
        FlashcardResponse r = materialService().flashcards(1L, 1L, NOTES);

        r.flashcards().forEach(c -> System.out.println("  Q: " + c.question() + " | A: " + c.answer()));
        assertThat(r.flashcards()).hasSize(10);
        assertThat(r.label()).isEqualTo("AI-generated");
    }

    @Test
    void realGeminiMakesFiveValidQuestions() {
        QuestionResponse r = materialService().questions(1L, 1L, NOTES);

        assertThat(r.questions()).hasSize(5).allSatisfy(q -> {
            assertThat(q.options()).hasSize(4);
            assertThat(q.correctOptionIndex()).isBetween(0, 3);
        });
    }

    @Test
    void realGeminiRewritePassesTheSafetyChecks() {
        String model = MODEL;
        ExplanationService service = new ExplanationService(client(), limiter(), new ObjectMapper());

        Explanation e = service.explain(new ExplanationInput(
                "lean_withdraw",
                "Leaning toward withdraw, but it's close",
                "high",
                52,
                61L,
                35,
                "88% average on the remaining work",
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
