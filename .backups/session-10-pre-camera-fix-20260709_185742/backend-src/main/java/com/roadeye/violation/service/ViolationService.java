package com.roadeye.violation.service;

import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.shared.ResourceNotFoundException;
import com.roadeye.violation.dto.ViolationResponse;
import com.roadeye.violation.entity.Violation;
import com.roadeye.violation.entity.ViolationEvidence;
import com.roadeye.violation.entity.ViolationReview;
import com.roadeye.violation.repository.ViolationEvidenceRepository;
import com.roadeye.violation.repository.ViolationRepository;
import com.roadeye.violation.repository.ViolationReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ViolationService {

    private final ViolationRepository violationRepository;
    private final ViolationEvidenceRepository evidenceRepository;
    private final ViolationReviewRepository reviewRepository;
    private final CameraRepository cameraRepository;

    public ViolationService(ViolationRepository violationRepository,
                            ViolationEvidenceRepository evidenceRepository,
                            ViolationReviewRepository reviewRepository,
                            CameraRepository cameraRepository) {
        this.violationRepository = violationRepository;
        this.evidenceRepository = evidenceRepository;
        this.reviewRepository = reviewRepository;
        this.cameraRepository = cameraRepository;
    }

    @Transactional(readOnly = true)
    public Page<ViolationResponse> getViolations(String status, String type, String cameraId, Pageable pageable) {
        Page<Violation> page;
        if (status != null) {
            page = violationRepository.findByStatus(status, pageable);
        } else if (type != null) {
            page = violationRepository.findByViolationType(type, pageable);
        } else if (cameraId != null) {
            page = violationRepository.findByCameraId(cameraId, pageable);
        } else {
            page = violationRepository.findAll(pageable);
        }

        List<Violation> violations = page.getContent();
        if (violations.isEmpty()) {
            return page.map(this::mapToResponse);
        }

        Set<String> cameraIds = violations.stream()
                .map(Violation::getCameraId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> cameraNames = batchLoadCameraNames(cameraIds);

        Map<String, String> evidenceIds = new HashMap<>();
        if (!violations.isEmpty()) {
            List<String> violationIdStrings = violations.stream()
                    .map(v -> v.getId().toString())
                    .collect(Collectors.toList());
            evidenceRepository.findByViolationIdIn(violationIdStrings).stream()
                    .collect(Collectors.groupingBy(ViolationEvidence::getViolationId))
                    .forEach((vid, evList) -> evidenceIds.put(vid, evList.get(0).getId().toString()));
        }

        return page.map(v -> {
            ViolationResponse.ViolationResponseBuilder builder = ViolationResponse.builder()
                    .id(v.getId())
                    .cameraId(v.getCameraId())
                    .cameraName(cameraNames.get(v.getCameraId()))
                    .timestamp(v.getViolatedAt())
                    .violationType(v.getViolationType())
                    .confidence(v.getConfidence())
                    .status(v.getStatus())
                    .vehicleCategory(v.getVehicleCategory())
                    .plateText(v.getPlateText())
                    .normalizedPlate(v.getNormalizedPlate())
                    .plateCountry(v.getPlateCountry())
                    .speedKmh(v.getSpeedKmh())
                    .speedLimit(v.getSpeedLimit())
                    .trackId(v.getTrackId())
                    .evidenceId(evidenceIds.get(v.getId().toString()))
                    .createdAt(v.getCreatedAt())
                    .detectorVersion(v.getDetectorVersion());
            return builder.build();
        });
    }

    @Transactional(readOnly = true)
    public ViolationResponse getViolation(UUID id) {
        Violation violation = violationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Violation", "id", id));
        return mapToResponse(violation);
    }

    public Violation createViolation(Violation violation) {
        if (violation.getStatus() == null) {
            violation.setStatus("DETECTED");
        }
        return violationRepository.save(violation);
    }

    public ViolationResponse updateStatus(UUID id, String newStatus, String comment, String reviewer) {
        Violation violation = violationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Violation", "id", id));
        String previousStatus = violation.getStatus();

        ViolationReview review = new ViolationReview();
        review.setViolationId(id.toString());
        review.setReviewer(reviewer);
        review.setPreviousStatus(previousStatus);
        review.setNewStatus(newStatus);
        review.setComment(comment);
        review.setReviewedAt(Instant.now());
        reviewRepository.save(review);

        violation.setStatus(newStatus);
        if (comment != null) {
            violation.setNotes(comment);
        }
        return mapToResponse(violationRepository.save(violation));
    }

    @Transactional(readOnly = true)
    public List<ViolationEvidence> getEvidence(UUID violationId) {
        return evidenceRepository.findByViolationId(violationId.toString());
    }

    public ViolationEvidence addEvidence(ViolationEvidence evidence) {
        return evidenceRepository.save(evidence);
    }

    private Map<String, String> batchLoadCameraNames(Set<String> cameraIds) {
        Map<String, String> names = new HashMap<>();
        if (cameraIds.isEmpty()) return names;
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

    private ViolationResponse mapToResponse(Violation violation) {
        String cameraName = null;
        if (violation.getCameraId() != null) {
            try {
                cameraName = cameraRepository.findById(UUID.fromString(violation.getCameraId()))
                        .map(c -> c.getName())
                        .orElse(null);
            } catch (IllegalArgumentException ignored) {
                // cameraId may not be a UUID in legacy data
            }
        }

        String evidenceId = evidenceRepository.findByViolationId(violation.getId().toString()).stream()
                .findFirst()
                .map(e -> e.getId().toString())
                .orElse(null);

        return ViolationResponse.builder()
                .id(violation.getId())
                .cameraId(violation.getCameraId())
                .cameraName(cameraName)
                .timestamp(violation.getViolatedAt())
                .violationType(violation.getViolationType())
                .confidence(violation.getConfidence())
                .status(violation.getStatus())
                .vehicleCategory(violation.getVehicleCategory())
                .plateText(violation.getPlateText())
                .normalizedPlate(violation.getNormalizedPlate())
                .plateCountry(violation.getPlateCountry())
                .speedKmh(violation.getSpeedKmh())
                .speedLimit(violation.getSpeedLimit())
                .trackId(violation.getTrackId())
                .evidenceId(evidenceId)
                .createdAt(violation.getCreatedAt())
                .detectorVersion(violation.getDetectorVersion())
                .build();
    }
}
