package com.roadeye.violation.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "violation_reviews", indexes = {
    @Index(name = "idx_vr_violation", columnList = "violation_id")
})
public class ViolationReview extends BaseEntity {
    @Column(name = "violation_id", nullable = false)
    private String violationId;

    @Column(name = "reviewer")
    private String reviewer;

    @Column(name = "previous_status", length = 50)
    private String previousStatus;

    @Column(name = "new_status", length = 50)
    private String newStatus;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
