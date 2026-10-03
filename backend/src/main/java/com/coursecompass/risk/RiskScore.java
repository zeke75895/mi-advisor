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

@Entity
@Table(name = "risk_scores")
public class RiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "risk_probability", nullable = false)
    private Double riskProbability;

    /** The model's 0/1 decision; the fusion logic needs it alongside the probability. */
    @Column(name = "at_risk", nullable = false)
    private boolean atRisk;

    @Column(name = "model_version", nullable = false, length = 40)
    private String modelVersion;

    /** Explanation, decision path and top features from the ML service, as JSON, for the risk card. */
    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    protected RiskScore() {}

    public RiskScore(
            Course course, Double riskProbability, boolean atRisk, String modelVersion, String detailsJson) {
        this.course = course;
        this.riskProbability = riskProbability;
        this.atRisk = atRisk;
        this.modelVersion = modelVersion;
        this.detailsJson = detailsJson;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public Double getRiskProbability() {
        return riskProbability;
    }

    public boolean isAtRisk() {
        return atRisk;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
