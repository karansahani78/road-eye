package com.roadeye.alert.service;

import com.roadeye.alert.dto.AlertResponse;
import com.roadeye.alert.entity.Alert;
import com.roadeye.alert.repository.AlertRepository;
import com.roadeye.camera.repository.CameraRepository;
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
    private final CameraRepository cameraRepository;

    public AlertService(AlertRepository alertRepository, CameraRepository cameraRepository) {
        this.alertRepository = alertRepository;
        this.cameraRepository = cameraRepository;
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlerts(String type, String severity, String cameraId, Boolean acknowledged, Pageable pageable) {
        Page<Alert> page;
        if (type != null) {
            page = alertRepository.findByType(type, pageable);
        } else if (severity != null) {
            page = alertRepository.findBySeverity(severity, pageable);
        } else if (cameraId != null) {
            page = alertRepository.findByCameraId(cameraId, pageable);
        } else if (acknowledged != null) {
            page = alertRepository.findByAcknowledged(acknowledged, pageable);
        } else {
            page = alertRepository.findAll(pageable);
        }
        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
        return mapToResponse(alert);
    }

    public Alert createAlert(Alert alert) {
        return alertRepository.save(alert);
    }

    public AlertResponse acknowledgeAlert(UUID id, String acknowledgedBy) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(Instant.now());
        return mapToResponse(alertRepository.save(alert));
    }

    public AlertResponse resolveAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
        alert.setResolved(true);
        alert.setResolvedAt(Instant.now());
        return mapToResponse(alertRepository.save(alert));
    }

    private AlertResponse mapToResponse(Alert alert) {
        String cameraName = null;
        if (alert.getCameraId() != null) {
            try {
                cameraName = cameraRepository.findById(UUID.fromString(alert.getCameraId()))
                        .map(c -> c.getName())
                        .orElse(null);
            } catch (IllegalArgumentException ignored) {
                // cameraId may not be a UUID
            }
        }

        return AlertResponse.builder()
                .id(alert.getId())
                .type(alert.getType())
                .severity(alert.getSeverity())
                .source(alert.getSource())
                .cameraId(alert.getCameraId())
                .cameraName(cameraName)
                .message(alert.getMessage())
                .acknowledged(alert.isAcknowledged())
                .acknowledgedBy(alert.getAcknowledgedBy())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .resolved(alert.isResolved())
                .resolvedAt(alert.getResolvedAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
