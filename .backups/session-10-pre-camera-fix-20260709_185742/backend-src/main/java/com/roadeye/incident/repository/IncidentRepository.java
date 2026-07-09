package com.roadeye.incident.repository;

import com.roadeye.incident.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Page<Incident> findByCameraId(String cameraId, Pageable pageable);
    Page<Incident> findByIncidentType(String incidentType, Pageable pageable);
    Page<Incident> findByStatus(String status, Pageable pageable);
    Page<Incident> findBySeverity(String severity, Pageable pageable);
    Page<Incident> findByCameraIdAndDetectedAtBetween(String cameraId, Instant start, Instant end, Pageable pageable);
    long countByStatus(String status);
    long countByStatusNot(String status);
    long countBySeverityAndStatusNot(String severity, String status);
    long countByCameraIdAndDetectedAtBetween(String cameraId, Instant start, Instant end);
    List<Incident> findByStatusNotOrderByDetectedAtDesc(String status);
}
