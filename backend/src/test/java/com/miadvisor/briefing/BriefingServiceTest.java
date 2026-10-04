package com.miadvisor.briefing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miadvisor.briefing.BriefingDtos.BriefingResponse;
import com.miadvisor.common.BadRequestException;
import com.miadvisor.course.Course;
import com.miadvisor.course.CourseService;
import com.miadvisor.llm.AiRateLimiter;
import com.miadvisor.llm.GeminiClient;
import com.miadvisor.recommendation.RecommendationDtos.RecommendationResponse;
import com.miadvisor.recommendation.RecommendationDtos.Signals;
import com.miadvisor.recommendation.RecommendationService;
import com.miadvisor.recommendation.RecommendationType;
import com.miadvisor.voice.ElevenLabsClient;
import com.miadvisor.voice.VoiceNotConfiguredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BriefingServiceTest {

    private static final byte[] MP3 = {1, 2, 3};

    private GeminiClient gemini;
    private ElevenLabsClient voice;
    private RecommendationService recommendations;
    private BriefingService service;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiClient.class);
        voice = mock(ElevenLabsClient.class);
        recommendations = mock(RecommendationService.class);
        CourseService courses = mock(CourseService.class);
        Course course = mock(Course.class);
        when(course.getCourseName()).thenReturn("General Chemistry");
        when(courses.getOwned(1L, 7L)).thenReturn(course);
        when(voice.isEnabled()).thenReturn(true);
        when(voice.speak(anyString())).thenReturn(MP3);
        when(recommendations.recommend(1L, 7L)).thenReturn(Optional.of(rec(RecommendationType.LEAN_WITHDRAW)));
        service = new BriefingService(courses, recommendations, gemini, voice,
                new AiRateLimiter(100, Duration.ofMinutes(10), 100, Clock.systemUTC()), new ObjectMapper());
    }

    @Test
    void speaksGeminiScriptWithDisclaimerAndAdvisorNoteAppended() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"script": "Here's where **General Chemistry** stands. Our prediction sees high risk, and you're \
                projected at 58. To finish with a C you'd need an 88% average on what's left. We're leaning \
                toward withdrawing, so meet with your professor this week."}
                """);

        BriefingResponse b = service.brief(1L, 7L);

        assertThat(b.source()).isEqualTo("gemini");
        assertThat(b.label()).isEqualTo("AI-generated");
        assertThat(b.script())
                .startsWith("Here's where General Chemistry stands.") // markdown stripped
                .endsWith(BriefingService.CLOSING + " " + BriefingService.ADVISOR_CLOSING);
        assertThat(Base64.getDecoder().decode(b.audioBase64())).isEqualTo(MP3);
        assertThat(b.mimeType()).isEqualTo("audio/mpeg");
        assertThat(b.disclaimer()).startsWith("Script written by Gemini, voice by ElevenLabs.").contains("not a verdict");
        verify(voice).speak(b.script());
    }

    @Test
    void fallsBackToTemplateWhenGeminiInventsANumber() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"script": "Your projected grade is 58, and 9 out of 10 students in your spot recover. Keep going!"}
                """);

        BriefingResponse b = service.brief(1L, 7L);

        assertThat(b.source()).isEqualTo("template");
        assertThat(b.script()).doesNotContain("9 out of 10")
                .contains("General Chemistry", "high risk", "58", "88 percent", "meet with your professor")
                .endsWith(BriefingService.ADVISOR_CLOSING);
    }

    @Test
    void usesTemplateWithoutGeminiAndSkipsAdvisorNoteForStay() {
        when(gemini.isEnabled()).thenReturn(false);
        when(recommendations.recommend(1L, 7L)).thenReturn(Optional.of(rec(RecommendationType.LEAN_STAY)));

        BriefingResponse b = service.brief(1L, 7L);

        assertThat(b.source()).isEqualTo("template");
        assertThat(b.script()).endsWith(BriefingService.CLOSING).doesNotContain("advisor");
        verify(gemini, never()).generateJson(anyString(), anyString(), any(), anyInt());
    }

    @Test
    void promptCarriesSignalsButNoPersonalData() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn("{}");
        service.brief(1L, 7L);

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), user.capture(), any(), anyInt());
        assertThat(user.getValue())
                .contains("Course: General Chemistry", "Recommendation: lean_withdraw", "Projected final grade: 58")
                .contains("88% average on the remaining work", "- Meet with your professor this week")
                .doesNotContain("@");
    }

    @Test
    void replayingTheSameBriefingDoesNotCallTheApisAgain() {
        when(gemini.isEnabled()).thenReturn(false);

        BriefingResponse first = service.brief(1L, 7L);
        BriefingResponse second = service.brief(1L, 7L);

        assertThat(second).isSameAs(first);
        verify(voice, times(1)).speak(anyString());
    }

    @Test
    void refusesWhenVoiceIsNotConfigured() {
        when(voice.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.brief(1L, 7L)).isInstanceOf(VoiceNotConfiguredException.class);
        verify(voice, never()).speak(anyString());
    }

    @Test
    void asksForARiskCheckFirstWhenThereIsNoRecommendation() {
        when(recommendations.recommend(1L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.brief(1L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("risk check");
    }

    private static RecommendationResponse rec(RecommendationType type) {
        return new RecommendationResponse(
                7L,
                type,
                0.6,
                0.6,
                "Leaning toward withdraw, but it's close",
                List.of("Your projected final grade is 58, below the 70 needed for a C."),
                List.of("Meet with your professor this week"),
                null,
                new Signals(0.82, true, 58.2, 61.0, 0.35, 0.65, 87.4, 92.0, 0.5, 1, 2),
                null,
                type.isWithdraw() ? "Talk to your advisor before withdrawing." : null,
                "This is guidance, not a verdict.",
                "tree_v1",
                Instant.parse("2026-10-04T12:00:00Z"));
    }
}
