package com.roadeye.violation.repository;

import com.roadeye.violation.entity.ViolationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ViolationReviewRepository extends JpaRepository<ViolationReview, UUID> {
    List<ViolationReview> findByViolationId(String violationId);
}
