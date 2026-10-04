package com.miadvisor.session;

import com.miadvisor.course.Course;
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
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

/** One block of studying a student logged for a course (user story US10: track real study hours). */
@Entity
@Table(name = "study_sessions")
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** The student's local calendar day. */
    @Column(name = "studied_on", nullable = false)
    private LocalDate studiedOn;

    @Column(nullable = false)
    private Integer minutes;

    @Column(length = 200)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudySession() {}

    public StudySession(Course course, LocalDate studiedOn, int minutes, String note) {
        this.course = course;
        this.studiedOn = studiedOn;
        this.minutes = minutes;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public LocalDate getStudiedOn() {
        return studiedOn;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public String getNote() {
        return note;
    }
}
