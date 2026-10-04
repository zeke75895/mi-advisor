package com.miadvisor.study;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StudyDtos {

    private StudyDtos() {}

    public static final String AI_LABEL = "AI-generated";
    public static final String MATERIAL_DISCLAIMER =
            "AI-generated from your notes. It can contain mistakes, so check it against your course materials.";
    public static final String PLAN_DISCLAIMER =
            "AI-generated suggestion. Adjust it to your real schedule and your instructor's guidance.";

    /** content is optional: when omitted, the course's saved notes (pasted or from a PDF) are used. */
    public record MaterialRequest(
            @Size(max = 20000, message = "send up to 20,000 characters, or save longer notes to the course") String content) {}

    public record Flashcard(String question, String answer) {}

    public record FlashcardResponse(
            Long courseId, String label, String disclaimer, List<Flashcard> flashcards, Instant generatedAt) {}

    public record Question(String question, List<String> options, int correctOptionIndex, String explanation) {}

    public record QuestionResponse(
            Long courseId, String label, String disclaimer, List<Question> questions, Instant generatedAt) {}

    /** timeZone is an IANA zone like "America/New_York", so due dates land on the student's calendar days. */
    public record StudyPlanRequest(
            LocalDate startDate,
            @Min(1) @Max(10) Integer hoursPerDay,
            List<Long> courseIds,
            @Size(max = 64) String timeZone) {}

    public record StudyTask(String course, String task, int minutes) {}

    public record StudyDay(LocalDate date, String dayOfWeek, String focus, List<StudyTask> tasks) {}

    public record StudyPlanResponse(
            String label,
            String disclaimer,
            LocalDate startDate,
            int hoursPerDay,
            List<String> deadlinesConsidered,
            List<String> weakTopicsConsidered,
            List<StudyDay> days,
            String timeZone,
            Instant generatedAt) {}
}
