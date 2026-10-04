package com.coursecompass.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coursecompass.course.Course;
import com.coursecompass.course.CourseService;
import com.coursecompass.item.GradeCategory;
import com.coursecompass.llm.AiRateLimiter;
import com.coursecompass.llm.GeminiClient;
import com.coursecompass.llm.GeminiException;
import com.coursecompass.notes.SyllabusItemService.ExtractionResponse;
import com.coursecompass.notes.SyllabusItemService.ItemSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyllabusItemServiceTest {

    // Shape of text PDFBox extracts from a syllabus grading table
    private static final String SYLLABUS = """
            CH 101: General Chemistry I
            Grading
            Component Weight Due
            Midterm exam 25% Week 7
            Homework sets 1-6 20% Weekly
            Quizzes 1-4 10% Weeks 3, 5, 9, 11
            Lab reports 20% After each lab
            Final exam (cumulative) 25% Finals week
            Policies
            Late homework loses 10% per day.
            Total 100%
            """;

    private GeminiClient gemini;
    private SyllabusItemService service;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiClient.class);
        CourseService courses = mock(CourseService.class);
        when(courses.getOwned(1L, 2L)).thenReturn(new Course(null, "CH 101", "General Chemistry", 4, null));
        NotesService notes = mock(NotesService.class);
        when(notes.textForAi(2L)).thenReturn(Optional.of(SYLLABUS));
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
        service = new SyllabusItemService(courses, notes, gemini,
                new AiRateLimiter(100, Duration.ofMinutes(10), 100, clock), new ObjectMapper(), clock);
    }

    @Test
    void patternMatchFindsWeightedItemsAndSkipsPolicies() {
        List<ItemSuggestion> items = service.fromPatterns(SYLLABUS);

        assertThat(items).extracting(ItemSuggestion::name)
                .containsExactly("Midterm exam", "Homework sets 1-6", "Quizzes 1-4", "Lab reports", "Final exam (cumulative)");
        assertThat(items).extracting(ItemSuggestion::category).containsExactly(
                GradeCategory.EXAM, GradeCategory.HOMEWORK, GradeCategory.QUIZ, GradeCategory.PROJECT, GradeCategory.EXAM);
        assertThat(items.stream().mapToDouble(ItemSuggestion::weight).sum()).isEqualTo(100.0);
    }

    @Test
    void patternMatchWorksOnTheRealDemoSyllabusPdf() throws Exception {
        java.nio.file.Path pdf = java.nio.file.Path.of("../docs/demo/ch101-syllabus.pdf");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(pdf));
        String text = new PdfTextExtractor().extract(java.nio.file.Files.readAllBytes(pdf));

        List<ItemSuggestion> items = service.fromPatterns(text);

        System.out.println("Demo syllabus suggestions: " + items);
        assertThat(items).hasSize(5);
        assertThat(items.stream().mapToDouble(ItemSuggestion::weight).sum()).isEqualTo(100.0);
    }

    @Test
    void usesPatternsWithoutAnApiKey() {
        when(gemini.isEnabled()).thenReturn(false);

        ExtractionResponse r = service.extract(1L, 2L);

        assertThat(r.source()).isEqualTo("pattern");
        assertThat(r.label()).isNull();
        assertThat(r.totalWeight()).isEqualTo(100.0);
        assertThat(r.items()).hasSize(5);
        verify(gemini, never()).generateJson(anyString(), anyString(), any(), anyInt());
    }

    @Test
    void usesGeminiAndKeepsOnlyValidItemsAndRealDates() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"items": [
                  {"name": "Midterm Exam", "category": "EXAM", "weight": 25, "dueDate": "2026-10-14"},
                  {"name": "Final Exam", "category": "EXAM", "weight": 25, "dueDate": "Finals week"},
                  {"name": "Labs", "category": "lab", "weight": 30, "dueDate": ""},
                  {"name": "Bonus", "category": "PROJECT", "weight": 0, "dueDate": ""},
                  {"name": "midterm exam", "category": "EXAM", "weight": 25, "dueDate": ""}
                ]}
                """);

        ExtractionResponse r = service.extract(1L, 2L);

        assertThat(r.source()).isEqualTo("gemini");
        assertThat(r.label()).isEqualTo("AI-generated");
        assertThat(r.items()).extracting(ItemSuggestion::name).containsExactly("Midterm Exam", "Final Exam", "Labs");
        assertThat(r.items().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(r.items().get(1).dueDate()).isNull(); // "Finals week" isn't a date
        assertThat(r.items().get(2).category()).isEqualTo(GradeCategory.PROJECT); // unknown category -> guessed
        assertThat(r.warnings()).anyMatch(w -> w.contains("add up to 80%"));
    }

    @Test
    void fallsBackToPatternsWhenGeminiFails() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenThrow(new GeminiException("overloaded"));

        ExtractionResponse r = service.extract(1L, 2L);

        assertThat(r.source()).isEqualTo("pattern");
        assertThat(r.items()).hasSize(5);
        assertThat(r.warnings()).anyMatch(w -> w.contains("pattern matching"));
    }
}
