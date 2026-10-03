package com.coursecompass.risk;

import com.coursecompass.course.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** The study-habit answers a student gave for a risk check, kept so any device can prefill the form. */
@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    private Double attendanceRate;
    private Integer missedDeadlines;
    private Double onTimeSubmissionRate;
    private Double avgPracticeQuizScore;
    /** As entered; null means "use the graded Midterm item". */
    private Double midtermScore;
    private Double avgWeeklyStudyHours;
    private Integer flashcardsReviewed;
    private Double avgDaysStartedBeforeExam;
    private Double lateNightStudyPct;
    private Double avgSleepHours;
    private Integer studySessionsLogged;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CheckIn() {}

    public CheckIn(Course course, RiskDtos.PredictRequest r) {
        this.course = course;
        this.attendanceRate = r.attendanceRate();
        this.missedDeadlines = r.missedDeadlines();
        this.onTimeSubmissionRate = r.onTimeSubmissionRate();
        this.avgPracticeQuizScore = r.avgPracticeQuizScore();
        this.midtermScore = r.midtermScore();
        this.avgWeeklyStudyHours = r.avgWeeklyStudyHours();
        this.flashcardsReviewed = r.flashcardsReviewed();
        this.avgDaysStartedBeforeExam = r.avgDaysStartedBeforeExam();
        this.lateNightStudyPct = r.lateNightStudyPct();
        this.avgSleepHours = r.avgSleepHours();
        this.studySessionsLogged = r.studySessionsLogged();
    }

    public RiskDtos.CheckInResponse toResponse() {
        return new RiskDtos.CheckInResponse(
                attendanceRate,
                missedDeadlines,
                onTimeSubmissionRate,
                avgPracticeQuizScore,
                midtermScore,
                avgWeeklyStudyHours,
                flashcardsReviewed,
                avgDaysStartedBeforeExam,
                lateNightStudyPct,
                avgSleepHours,
                studySessionsLogged,
                createdAt);
    }
}
