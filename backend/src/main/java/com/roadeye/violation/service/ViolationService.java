package com.roadeye.violation.service;

import com.roadeye.shared.ResourceNotFoundException;
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
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ViolationService {

    private final ViolationRepository violationRepository;
    private final ViolationEvidenceRepository evidenceRepository;
    private final ViolationReviewRepository reviewRepository;

    public ViolationService(ViolationRepository violationRepository,
                            ViolationEvidenceRepository evidenceRepository,
                            ViolationReviewRepository reviewRepository) {
        this.violationRepository = violationRepository;
        this.evidenceRepository = evidenceRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<Violation> getViolations(String status, String type, String cameraId, Pageable pageable) {
        if (status != null) {
            return violationRepository.findByStatus(status, pageable);
        }
        if (type != null) {
            return violationRepository.findByViolationType(type, pageable);
        }
        if (cameraId != null) {
            return violationRepository.findByCameraId(cameraId, pageable);
        }
        return violationRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Violation getViolation(UUID id) {
        return violationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Violation", "id", id));
    }

    public Violation createViolation(Violation violation) {
        if (violation.getStatus() == null) {
            violation.setStatus("DETECTED");
        }
        return violationRepository.save(violation);
    }

    public Violation updateStatus(UUID id, String newStatus, String comment, String reviewer) {
        Violation violation = getViolation(id);
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
        return violationRepository.save(violation);
    }

    @Transactional(readOnly = true)
    public List<ViolationEvidence> getEvidence(UUID violationId) {
        return evidenceRepository.findByViolationId(violationId.toString());
    }

    public ViolationEvidence addEvidence(ViolationEvidence evidence) {
        return evidenceRepository.save(evidence);
    }
}
