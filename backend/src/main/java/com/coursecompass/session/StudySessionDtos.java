package com.coursecompass.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class StudySessionDtos {

    private StudySessionDtos() {}

    public record LogRequest(
            @NotNull @Min(value = 5, message = "log at least 5 minutes") @Max(value = 720, message = "log at most 12 hours at once")
                    Integer minutes,
            /** The student's local date; defaults to today. */
            LocalDate studiedOn,
            @Size(max = 200) String note) {}

    public record SessionResponse(Long id, Long courseId, LocalDate studiedOn, int minutes, String note) {

        static SessionResponse from(StudySession s) {
            return new SessionResponse(s.getId(), s.getCourse().getId(), s.getStudiedOn(), s.getMinutes(), s.getNote());
        }
    }

    /**
     * @param last7DaysMinutes minutes logged in the 7 days ending today
     * @param avgWeeklyHours average hours per week over the last 4 weeks (or since the first session, if
     *     more recent), matching the model's avg_weekly_study_hours input
     * @param totalSessions every session ever logged, matching the model's study_sessions_logged input
     */
    public record CourseSummary(
            Long courseId, String courseCode, int last7DaysMinutes, double avgWeeklyHours, long totalSessions) {}

    public record CourseLog(CourseSummary summary, List<SessionResponse> sessions) {}

    /** @param dailyMinutes minutes per day for the last 7 days, oldest first, across all courses */
    public record DayTotal(LocalDate date, int minutes) {}

    public record WeekSummary(LocalDate today, int last7DaysMinutes, List<DayTotal> dailyMinutes, List<CourseSummary> courses) {}
}
