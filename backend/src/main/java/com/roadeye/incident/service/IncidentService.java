package com.roadeye.incident.service;

import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.incident.dto.IncidentCreateRequest;
import com.roadeye.incident.dto.IncidentResponse;
import com.roadeye.incident.dto.IncidentUpdateRequest;
import com.roadeye.incident.entity.Incident;
import com.roadeye.incident.repository.IncidentRepository;
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
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final CameraRepository cameraRepository;

    public IncidentService(IncidentRepository incidentRepository, CameraRepository cameraRepository) {
        this.incidentRepository = incidentRepository;
        this.cameraRepository = cameraRepository;
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(String cameraId, String type, String status, String severity, Pageable pageable) {
        Page<Incident> page;
        if (cameraId != null) {
            page = incidentRepository.findByCameraId(cameraId, pageable);
        } else if (type != null) {
            page = incidentRepository.findByIncidentType(type, pageable);
        } else if (status != null) {
            page = incidentRepository.findByStatus(status, pageable);
        } else if (severity != null) {
            page = incidentRepository.findBySeverity(severity, pageable);
        } else {
            page = incidentRepository.findAll(pageable);
        }

        Map<String, String> cameraNames = loadCameraNames(page.getContent());
        return page.map(i -> mapToResponse(i, cameraNames));
    }

    private Map<String, String> loadCameraNames(List<Incident> incidents) {
        Set<String> cameraIds = incidents.stream()
                .map(Incident::getCameraId)
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

    private IncidentResponse mapToResponse(Incident incident) {
        return mapToResponse(incident, loadCameraNames(List.of(incident)));
    }

    private IncidentResponse mapToResponse(Incident incident, Map<String, String> cameraNames) {
        String cid = incident.getCameraId();
        return IncidentResponse.builder()
                .id(incident.getId())
                .cameraId(cid)
                .cameraName(cid != null ? cameraNames.get(cid) : null)
                .incidentType(incident.getIncidentType())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .latitude(incident.getLatitude())
                .longitude(incident.getLongitude())
                .description(incident.getDescription())
                .detectedAt(incident.getDetectedAt())
                .occurredAt(incident.getOccurredAt())
                .resolvedAt(incident.getResolvedAt())
                .assignedTo(incident.getAssignedTo())
                .assignedAt(incident.getAssignedAt())
                .resolutionNotes(incident.getResolutionNotes())
                .source(incident.getSource())
                .confidence(incident.getConfidence())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", id));
        return mapToResponse(incident);
    }

    public Incident createIncident(Incident entity) {
        if (entity.getStatus() == null) {
            entity.setStatus("DETECTED");
        }
        if (entity.getSeverity() == null) {
            entity.setSeverity("MEDIUM");
        }
        if (entity.getDetectedAt() == null) {
            entity.setDetectedAt(Instant.now());
        }
        return incidentRepository.save(entity);
    }

    public IncidentResponse createFromRequest(IncidentCreateRequest request) {
        Incident incident = new Incident();
        incident.setCameraId(request.getCameraId());
        incident.setIncidentType(request.getIncidentType());
        incident.setSeverity(request.getSeverity() != null ? request.getSeverity() : "MEDIUM");
        incident.setStatus("DETECTED");
        incident.setLatitude(request.getLatitude());
        incident.setLongitude(request.getLongitude());
        incident.setDescription(request.getDescription());
        incident.setDetectedAt(Instant.now());
        incident.setOccurredAt(request.getOccurredAt());
        incident.setConfidence(request.getConfidence());
        incident.setSource(request.getSource() != null ? request.getSource() : "OPERATOR");
        incident = incidentRepository.save(incident);
        return mapToResponse(incident);
    }

    public IncidentResponse updateIncident(UUID id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", id));

        if (request.getStatus() != null) {
            incident.setStatus(request.getStatus());
            if ("RESOLVED".equals(request.getStatus()) || "CLOSED".equals(request.getStatus())) {
                incident.setResolvedAt(Instant.now());
            }
        }
        if (request.getAssignedTo() != null) {
            incident.setAssignedTo(request.getAssignedTo());
            incident.setAssignedAt(Instant.now());
        }
        if (request.getResolutionNotes() != null) {
            incident.setResolutionNotes(request.getResolutionNotes());
        }
        if (request.getDescription() != null) {
            incident.setDescription(request.getDescription());
        }

        return mapToResponse(incidentRepository.save(incident));
    }

    public IncidentResponse assignIncident(UUID id, String assignee) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", id));
        incident.setAssignedTo(assignee);
        incident.setAssignedAt(Instant.now());
        incident.setStatus("ASSIGNED");
        return mapToResponse(incidentRepository.save(incident));
    }

    public IncidentResponse resolveIncident(UUID id, String resolutionNotes) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", id));
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(Instant.now());
        if (resolutionNotes != null) {
            incident.setResolutionNotes(resolutionNotes);
        }
        return mapToResponse(incidentRepository.save(incident));
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return incidentRepository.countByStatusNot("RESOLVED");
    }

    @Transactional(readOnly = true)
    public long countBySeverity(String severity) {
        return incidentRepository.countBySeverityAndStatusNot(severity, "RESOLVED");
    }

}
