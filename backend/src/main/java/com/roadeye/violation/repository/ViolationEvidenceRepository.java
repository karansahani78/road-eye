package com.roadeye.violation.repository;

import com.roadeye.violation.entity.ViolationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ViolationEvidenceRepository extends JpaRepository<ViolationEvidence, UUID> {
    List<ViolationEvidence> findByViolationId(String violationId);
    List<ViolationEvidence> findByViolationIdIn(List<String> violationIds);
}
