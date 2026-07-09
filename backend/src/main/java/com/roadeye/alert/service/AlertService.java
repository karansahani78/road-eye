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
import java.util.*;
import java.util.stream.Collectors;

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
        Map<String, String> cameraNames = loadCameraNames(page.getContent());
        return page.map(a -> mapToResponse(a, cameraNames));
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
        Map<String, String> cameraNames = loadCameraNames(List.of(alert));
        return mapToResponse(alert, cameraNames);
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
        Alert saved = alertRepository.save(alert);
        Map<String, String> cameraNames = loadCameraNames(List.of(saved));
        return mapToResponse(saved, cameraNames);
    }

    public AlertResponse resolveAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", id));
        alert.setResolved(true);
        alert.setResolvedAt(Instant.now());
        Alert saved = alertRepository.save(alert);
        Map<String, String> cameraNames = loadCameraNames(List.of(saved));
        return mapToResponse(saved, cameraNames);
    }

    private Map<String, String> loadCameraNames(List<Alert> alerts) {
        Set<String> cameraIds = alerts.stream()
                .map(Alert::getCameraId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (cameraIds.isEmpty()) return new HashMap<>();
        Map<String, String> names = new HashMap<>();
        List<UUID> uuids = new ArrayList<>();
        for (String cid : cameraIds) {
            try {
                uuids.add(UUID.fromString(cid));
            } catch (IllegalArgumentException ignored) {}
        }
        if (!uuids.isEmpty()) {
            cameraRepository.findAllById(uuids)
                    .forEach(c -> names.put(c.getId().toString(), c.getName()));
        }
        return names;
    }

    private AlertResponse mapToResponse(Alert alert, Map<String, String> cameraNames) {
        return AlertResponse.builder()
                .id(alert.getId())
                .type(alert.getType())
                .severity(alert.getSeverity())
                .source(alert.getSource())
                .cameraId(alert.getCameraId())
                .cameraName(alert.getCameraId() != null ? cameraNames.get(alert.getCameraId()) : null)
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
