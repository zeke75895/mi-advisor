package com.miadvisor.study;

import com.miadvisor.user.User;
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

/** A generated 7-day study plan, stored as its JSON response. */
@Entity
@Table(name = "study_plans")
public class StudyPlanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "plan_json", nullable = false, columnDefinition = "text")
    private String planJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudyPlanRecord() {}

    public StudyPlanRecord(User user, String planJson) {
        this.user = user;
        this.planJson = planJson;
    }

    public String getPlanJson() {
        return planJson;
    }
}
