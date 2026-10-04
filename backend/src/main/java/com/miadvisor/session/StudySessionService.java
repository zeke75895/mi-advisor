package com.miadvisor.session;

import com.miadvisor.common.BadRequestException;
import com.miadvisor.common.NotFoundException;
import com.miadvisor.course.Course;
import com.miadvisor.course.CourseRepository;
import com.miadvisor.course.CourseService;
import com.miadvisor.session.StudySessionDtos.CourseLog;
import com.miadvisor.session.StudySessionDtos.CourseSummary;
import com.miadvisor.session.StudySessionDtos.DayTotal;
import com.miadvisor.session.StudySessionDtos.LogRequest;
import com.miadvisor.session.StudySessionDtos.SessionResponse;
import com.miadvisor.session.StudySessionDtos.WeekSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Study-hour tracking. Totals are summed in Java rather than with database-specific aggregates, so the
 * same code runs on Postgres and the H2 test database.
 */
@Service
public class StudySessionService {

    static final int WEEKS_FOR_AVERAGE = 4;

    private final StudySessionRepository sessions;
    private final CourseService courseService;
    private final CourseRepository courses;
    private final Clock clock;

    public StudySessionService(
            StudySessionRepository sessions, CourseService courseService, CourseRepository courses, Clock clock) {
        this.sessions = sessions;
        this.courseService = courseService;
        this.courses = courses;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse log(Long userId, Long courseId, LogRequest request, LocalDate today) {
        Course course = courseService.getOwned(userId, courseId);
        LocalDate day = request.studiedOn() != null ? request.studiedOn() : today(today);
        if (day.isAfter(today(today))) {
            throw new BadRequestException("You can't log study time for a future date.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        return SessionResponse.from(sessions.save(new StudySession(course, day, request.minutes(), note)));
    }

    @Transactional(readOnly = true)
    public CourseLog courseLog(Long userId, Long courseId, LocalDate today) {
        Course course = courseService.getOwned(userId, courseId);
        List<SessionResponse> recent = sessions.findTop50ByCourseIdOrderByStudiedOnDescIdDesc(courseId).stream()
                .map(SessionResponse::from)
                .toList();
        return new CourseLog(summary(course, today(today)), recent);
    }

    @Transactional(readOnly = true)
    public WeekSummary weekSummary(Long userId, LocalDate today) {
        LocalDate end = today(today);
        LocalDate start = end.minusDays(6);
        Map<LocalDate, Integer> byDay = sessions.findByCourseUserIdAndStudiedOnBetween(userId, start, end).stream()
                .collect(Collectors.groupingBy(StudySession::getStudiedOn, Collectors.summingInt(StudySession::getMinutes)));
        List<DayTotal> daily = start.datesUntil(end.plusDays(1))
                .map(d -> new DayTotal(d, byDay.getOrDefault(d, 0)))
                .toList();
        List<CourseSummary> perCourse = courses.findByUserIdOrderByIdAsc(userId).stream()
                .map(c -> summary(c, end))
                .toList();
        return new WeekSummary(end, daily.stream().mapToInt(DayTotal::minutes).sum(), daily, perCourse);
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        StudySession session = sessions.findById(sessionId)
                .filter(s -> s.getCourse().getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Study session " + sessionId + " not found"));
        sessions.delete(session);
    }

    private CourseSummary summary(Course course, LocalDate today) {
        List<StudySession> lastFourWeeks = sessions.findByCourseIdAndStudiedOnBetween(
                course.getId(), today.minusDays(WEEKS_FOR_AVERAGE * 7L - 1), today);
        int last7 = lastFourWeeks.stream()
                .filter(s -> !s.getStudiedOn().isBefore(today.minusDays(6)))
                .mapToInt(StudySession::getMinutes)
                .sum();
        int fourWeekMinutes = lastFourWeeks.stream().mapToInt(StudySession::getMinutes).sum();
        // Average over the weeks since the first logged session (1 to 4), so someone who started logging
        // this week isn't divided by 4 weeks they never tracked.
        long weeks = sessions.findFirstByCourseIdOrderByStudiedOnAsc(course.getId())
                .map(first -> (java.time.temporal.ChronoUnit.DAYS.between(first.getStudiedOn(), today) + 7) / 7)
                .map(w -> Math.max(1, Math.min(WEEKS_FOR_AVERAGE, w)))
                .orElse(1L);
        double avgWeeklyHours = Math.round(fourWeekMinutes / 60.0 / weeks * 10) / 10.0;
        return new CourseSummary(course.getId(), course.getCourseCode(), last7, avgWeeklyHours,
                sessions.countByCourseId(course.getId()));
    }

    private LocalDate today(LocalDate fromClient) {
        return fromClient != null ? fromClient : LocalDate.now(clock);
    }
}
