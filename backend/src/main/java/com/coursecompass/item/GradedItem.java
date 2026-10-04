package com.coursecompass.item;

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
import java.time.OffsetDateTime;

@Entity
@Table(name = "graded_items")
public class GradedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeCategory category;

    /** Percent of the final course grade this item is worth (0-100), from the syllabus. */
    @Column(nullable = false)
    private Double weight;

    @Column(name = "points_possible")
    private Double pointsPossible;

    @Column(name = "points_earned")
    private Double pointsEarned;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "is_graded", nullable = false)
    private boolean graded;

    protected GradedItem() {}

    public GradedItem(
            Course course,
            String name,
            GradeCategory category,
            Double weight,
            Double pointsPossible,
            Double pointsEarned,
            OffsetDateTime dueDate,
            boolean graded) {
        this.course = course;
        this.name = name;
        this.category = category;
        this.weight = weight;
        this.pointsPossible = pointsPossible;
        this.pointsEarned = pointsEarned;
        this.dueDate = dueDate;
        this.graded = graded;
    }

    public void update(
            String name,
            GradeCategory category,
            Double weight,
            Double pointsPossible,
            Double pointsEarned,
            OffsetDateTime dueDate,
            boolean graded) {
        this.name = name;
        this.category = category;
        this.weight = weight;
        this.pointsPossible = pointsPossible;
        this.pointsEarned = pointsEarned;
        this.dueDate = dueDate;
        this.graded = graded;
    }

    /** Score as a percentage, or null if not graded yet. */
    public Double percentScore() {
        if (!graded || pointsEarned == null || pointsPossible == null || pointsPossible <= 0) {
            return null;
        }
        return pointsEarned * 100.0 / pointsPossible;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getName() {
        return name;
    }

    public GradeCategory getCategory() {
        return category;
    }

    public Double getWeight() {
        return weight;
    }

    public Double getPointsPossible() {
        return pointsPossible;
    }

    public Double getPointsEarned() {
        return pointsEarned;
    }

    public OffsetDateTime getDueDate() {
        return dueDate;
    }

    public boolean isGraded() {
        return graded;
    }
}
