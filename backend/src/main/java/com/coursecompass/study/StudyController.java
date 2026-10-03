package com.coursecompass.study;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.study.StudyDtos.FlashcardResponse;
import com.coursecompass.study.StudyDtos.MaterialRequest;
import com.coursecompass.study.StudyDtos.QuestionResponse;
import com.coursecompass.study.StudyDtos.StudyPlanRequest;
import com.coursecompass.study.StudyDtos.StudyPlanResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyController {

    private final StudyMaterialService materialService;
    private final StudyPlanService planService;

    public StudyController(StudyMaterialService materialService, StudyPlanService planService) {
        this.materialService = materialService;
        this.planService = planService;
    }

    @PostMapping("/api/materials/{courseId}/generate-flashcards")
    public FlashcardResponse flashcards(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId, @Valid @RequestBody MaterialRequest request) {
        return materialService.flashcards(CurrentUser.id(jwt), courseId, request.content());
    }

    /** Latest saved flashcard set for the course; 204 if none yet. */
    @GetMapping("/api/materials/{courseId}/flashcards")
    public ResponseEntity<FlashcardResponse> latestFlashcards(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return materialService.latestFlashcards(CurrentUser.id(jwt), courseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/materials/{courseId}/generate-questions")
    public QuestionResponse questions(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId, @Valid @RequestBody MaterialRequest request) {
        return materialService.questions(CurrentUser.id(jwt), courseId, request.content());
    }

    /** Latest saved quiz for the course; 204 if none yet. */
    @GetMapping("/api/materials/{courseId}/questions")
    public ResponseEntity<QuestionResponse> latestQuestions(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return materialService.latestQuestions(CurrentUser.id(jwt), courseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/study-plan/generate")
    public StudyPlanResponse studyPlan(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody(required = false) StudyPlanRequest request) {
        return planService.generate(
                CurrentUser.id(jwt), request != null ? request : new StudyPlanRequest(null, null, null, null));
    }

    /** Latest saved study plan; 204 if none yet. */
    @GetMapping("/api/study-plan/latest")
    public ResponseEntity<StudyPlanResponse> latestPlan(@AuthenticationPrincipal Jwt jwt) {
        return planService.latest(CurrentUser.id(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
