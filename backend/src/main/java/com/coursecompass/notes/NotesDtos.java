package com.coursecompass.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class NotesDtos {

    private NotesDtos() {}

    public static final int MAX_NOTES_CHARS = 100_000;
    /** How much of the notes the AI study tools read at once. */
    public static final int MAX_AI_CHARS = 20_000;

    public record NotesRequest(
            @NotBlank @Size(max = MAX_NOTES_CHARS, message = "notes can be up to 100,000 characters") String content) {}

    /**
     * @param usedForAi true when the notes are longer than the AI reads, so only the first part is used
     */
    public record NotesResponse(
            Long courseId,
            String content,
            String source,
            String fileName,
            int length,
            boolean truncatedForAi,
            Instant updatedAt) {

        static NotesResponse from(CourseNotes notes) {
            return new NotesResponse(
                    notes.getCourse().getId(),
                    notes.getContent(),
                    notes.getSource().name(),
                    notes.getFileName(),
                    notes.getContent().length(),
                    notes.getContent().length() > MAX_AI_CHARS,
                    notes.getUpdatedAt());
        }
    }
}
