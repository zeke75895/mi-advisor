package com.coursecompass.session;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.session.StudySessionDtos.CourseLog;
import com.coursecompass.session.StudySessionDtos.LogRequest;
import com.coursecompass.session.StudySessionDtos.SessionResponse;
import com.coursecompass.session.StudySessionDtos.WeekSummary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "today" is the browser's local date, so weeks line up with the student's calendar. */
@RestController
public class StudySessionController {

    private final StudySessionService sessionService;

    public StudySessionController(StudySessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/api/courses/{courseId}/study-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse log(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
            @Valid @RequestBody LogRequest request) {
        return sessionService.log(CurrentUser.id(jwt), courseId, request, today);
    }

    @GetMapping("/api/courses/{courseId}/study-sessions")
    public CourseLog courseLog(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return sessionService.courseLog(CurrentUser.id(jwt), courseId, today);
    }

    @GetMapping("/api/study-sessions/summary")
    public WeekSummary summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return sessionService.weekSummary(CurrentUser.id(jwt), today);
    }

    @DeleteMapping("/api/study-sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
        sessionService.delete(CurrentUser.id(jwt), sessionId);
    }
}
