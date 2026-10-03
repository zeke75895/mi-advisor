package com.coursecompass.study;

import com.coursecompass.course.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** A generated flashcard set or quiz, stored as its JSON response so any device can reopen it. */
@Entity
@Table(name = "generated_materials")
public class GeneratedMaterial {

    public enum Kind {
        FLASHCARDS,
        QUESTIONS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GeneratedMaterial() {}

    public GeneratedMaterial(Course course, Kind kind, String contentJson) {
        this.course = course;
        this.kind = kind;
        this.contentJson = contentJson;
    }

    public String getContentJson() {
        return contentJson;
    }
}
