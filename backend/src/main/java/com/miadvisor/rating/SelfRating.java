package com.miadvisor.rating;

import com.miadvisor.item.GradedItem;
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

/** A student's 1-10 confidence rating for a graded item. Ratings are kept as history; the latest wins. */
@Entity
@Table(name = "self_ratings")
public class SelfRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graded_item_id", nullable = false)
    private GradedItem gradedItem;

    @Column(nullable = false)
    private Integer rating;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SelfRating() {}

    public SelfRating(GradedItem gradedItem, Integer rating) {
        this.gradedItem = gradedItem;
        this.rating = rating;
    }

    public Long getId() {
        return id;
    }

    public GradedItem getGradedItem() {
        return gradedItem;
    }

    public Integer getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
