package com.roadeye.alert.repository;

import com.roadeye.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Page<Alert> findByType(String type, Pageable pageable);
    Page<Alert> findBySeverity(String severity, Pageable pageable);
    Page<Alert> findByCameraId(String cameraId, Pageable pageable);
    Page<Alert> findByAcknowledged(boolean acknowledged, Pageable pageable);

    long countBySeverityAndAcknowledged(String severity, boolean acknowledged);
}
