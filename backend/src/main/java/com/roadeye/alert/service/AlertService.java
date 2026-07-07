package com.roadeye.alert.service;

import com.roadeye.alert.entity.Alert;
import com.roadeye.alert.repository.AlertRepository;
import com.roadeye.shared.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional(readOnly = true)
    public Page<Alert> getAlerts(String type, String severity, String cameraId, Boolean acknowledged, Pageable pageable) {
        if (type != null) return alertRepository.findByType(type, pageable);
        if (severity != null) return alertRepository.findBySeverity(severity, pageable);
        if (cameraId != null) return alertRepository.findByCameraId(cameraId, pageable);
        if (acknowledged != null) return alertRepository.findByAcknowledged(acknowledged, pageable);
        return alertRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Alert getAlert(UUID id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
    }

    public Alert createAlert(Alert alert) {
        return alertRepository.save(alert);
    }

    public Alert acknowledgeAlert(UUID id, String acknowledgedBy) {
        Alert alert = getAlert(id);
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(Instant.now());
        return alertRepository.save(alert);
    }

    public Alert resolveAlert(UUID id) {
        Alert alert = getAlert(id);
        alert.setResolved(true);
        alert.setResolvedAt(Instant.now());
        return alertRepository.save(alert);
    }
}
