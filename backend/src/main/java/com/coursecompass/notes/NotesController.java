package com.coursecompass.notes;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.notes.NotesDtos.NotesRequest;
import com.coursecompass.notes.NotesDtos.NotesResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses/{courseId}/notes")
public class NotesController {

    private final NotesService notesService;

    public NotesController(NotesService notesService) {
        this.notesService = notesService;
    }

    /** 204 when the course has no notes yet. */
    @GetMapping
    public ResponseEntity<NotesResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return notesService.get(CurrentUser.id(jwt), courseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public NotesResponse save(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId, @Valid @RequestBody NotesRequest request) {
        return notesService.save(CurrentUser.id(jwt), courseId, request.content());
    }

    /** Upload a syllabus or notes PDF; its text becomes the course notes used by flashcards and quizzes. */
    @PostMapping(path = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NotesResponse uploadPdf(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId, @RequestParam("file") MultipartFile file) {
        return notesService.uploadPdf(CurrentUser.id(jwt), courseId, file);
    }
}
