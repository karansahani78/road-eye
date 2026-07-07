package com.roadeye.violation.repository;

import com.roadeye.violation.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {
    Page<Violation> findByStatus(String status, Pageable pageable);
    Page<Violation> findByViolationType(String violationType, Pageable pageable);
    Page<Violation> findByCameraId(String cameraId, Pageable pageable);
    Page<Violation> findByCameraIdAndViolatedAtBetween(String cameraId, Instant start, Instant end, Pageable pageable);
}
