package com.miadvisor.notes;

import com.miadvisor.common.BadRequestException;
import com.miadvisor.course.Course;
import com.miadvisor.course.CourseService;
import com.miadvisor.notes.CourseNotes.Source;
import com.miadvisor.notes.NotesDtos.NotesResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NotesService {

    private final CourseService courseService;
    private final CourseNotesRepository notes;
    private final PdfTextExtractor pdf;

    public NotesService(CourseService courseService, CourseNotesRepository notes, PdfTextExtractor pdf) {
        this.courseService = courseService;
        this.notes = notes;
        this.pdf = pdf;
    }

    @Transactional(readOnly = true)
    public Optional<NotesResponse> get(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        return notes.findByCourseId(courseId).map(NotesResponse::from);
    }

    @Transactional
    public NotesResponse save(Long userId, Long courseId, String content) {
        return store(courseService.getOwned(userId, courseId), content.strip(), Source.PASTE, null);
    }

    @Transactional
    public NotesResponse uploadPdf(Long userId, Long courseId, MultipartFile file) {
        Course course = courseService.getOwned(userId, courseId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a PDF to upload.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The upload didn't finish. Please try again.");
        }
        String text = pdf.extract(bytes);
        if (text.length() > NotesDtos.MAX_NOTES_CHARS) {
            text = text.substring(0, NotesDtos.MAX_NOTES_CHARS);
        }
        return store(course, text, Source.PDF, file.getOriginalFilename());
    }

    /** Text the AI study tools should read: the first 20,000 characters of the saved notes. */
    @Transactional(readOnly = true)
    public Optional<String> textForAi(Long courseId) {
        return notes.findByCourseId(courseId)
                .map(CourseNotes::getContent)
                .map(c -> c.length() > NotesDtos.MAX_AI_CHARS ? c.substring(0, NotesDtos.MAX_AI_CHARS) : c);
    }

    private NotesResponse store(Course course, String content, Source source, String fileName) {
        CourseNotes entry = notes.findByCourseId(course.getId()).orElseGet(() -> new CourseNotes(course));
        entry.replace(content, source, fileName);
        return NotesResponse.from(notes.saveAndFlush(entry));
    }
}
