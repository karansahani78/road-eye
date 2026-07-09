package com.roadeye.violation;

import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.violation.entity.Violation;
import com.roadeye.violation.entity.ViolationEvidence;
import com.roadeye.violation.repository.ViolationEvidenceRepository;
import com.roadeye.violation.repository.ViolationRepository;
import com.roadeye.violation.repository.ViolationReviewRepository;
import com.roadeye.violation.service.ViolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock
    private ViolationRepository violationRepository;

    @Mock
    private ViolationEvidenceRepository evidenceRepository;

    @Mock
    private ViolationReviewRepository reviewRepository;

    @Mock
    private CameraRepository cameraRepository;

    private ViolationService violationService;

    @BeforeEach
    void setUp() {
        violationService = new ViolationService(violationRepository, evidenceRepository,
                reviewRepository, cameraRepository);
    }

    @Test
    void createViolation_setsDefaultStatus() {
        Violation input = new Violation();
        input.setCameraId("cam-1");
        input.setViolationType("OVERSPEEDING");
        input.setStatus(null);

        Violation saved = new Violation();
        saved.setId(UUID.randomUUID());
        saved.setCameraId("cam-1");
        saved.setViolationType("OVERSPEEDING");
        saved.setStatus("DETECTED");

        when(violationRepository.save(any(Violation.class))).thenReturn(saved);

        Violation result = violationService.createViolation(input);
        assertEquals("DETECTED", result.getStatus());
    }

    @Test
    void createViolation_preservesProvidedStatus() {
        Violation input = new Violation();
        input.setCameraId("cam-1");
        input.setViolationType("OVERSPEEDING");
        input.setStatus("PENDING_REVIEW");

        Violation saved = new Violation();
        saved.setId(UUID.randomUUID());
        saved.setCameraId("cam-1");
        saved.setViolationType("OVERSPEEDING");
        saved.setStatus("PENDING_REVIEW");

        when(violationRepository.save(any(Violation.class))).thenReturn(saved);

        Violation result = violationService.createViolation(input);
        assertEquals("PENDING_REVIEW", result.getStatus());
    }

    @Test
    void addEvidence_createsEvidenceRecord() {
        ViolationEvidence evidence = new ViolationEvidence();
        evidence.setViolationId(UUID.randomUUID().toString());
        evidence.setEvidenceType("FRAME");
        evidence.setBucketName("test-bucket");
        evidence.setObjectKey("test-key");

        when(evidenceRepository.save(any(ViolationEvidence.class))).thenReturn(evidence);

        ViolationEvidence result = violationService.addEvidence(evidence);
        assertNotNull(result);
        assertEquals("FRAME", result.getEvidenceType());
        assertEquals("test-bucket", result.getBucketName());
    }
}
