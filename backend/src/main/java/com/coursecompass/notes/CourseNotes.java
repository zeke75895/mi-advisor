package com.coursecompass.notes;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/** One set of study notes per course (pasted or extracted from an uploaded PDF); the study tools use it. */
@Entity
@Table(name = "course_notes")
public class CourseNotes {

    public enum Source {
        PASTE,
        PDF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    private Course course;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseNotes() {}

    public CourseNotes(Course course) {
        this.course = course;
    }

    public void replace(String content, Source source, String fileName) {
        this.content = content;
        this.source = source;
        this.fileName = fileName;
    }

    public Course getCourse() {
        return course;
    }

    public String getContent() {
        return content;
    }

    public Source getSource() {
        return source;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
