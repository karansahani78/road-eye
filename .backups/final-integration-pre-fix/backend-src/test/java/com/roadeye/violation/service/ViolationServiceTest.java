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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        violationService = new ViolationService(
                violationRepository, evidenceRepository,
                reviewRepository, cameraRepository);
    }

    @Test
    void createViolation_shouldSetDefaultStatus() {
        Violation violation = new Violation();
        violation.setCameraId("test-camera");
        violation.setViolationType("OVERSPEEDING");

        when(violationRepository.save(any(Violation.class))).thenAnswer(i -> i.getArgument(0));

        Violation result = violationService.createViolation(violation);

        assertEquals("DETECTED", result.getStatus());
        verify(violationRepository).save(violation);
    }

    @Test
    void createViolation_shouldPreserveExistingStatus() {
        Violation violation = new Violation();
        violation.setCameraId("test-camera");
        violation.setViolationType("OVERSPEEDING");
        violation.setStatus("PENDING_REVIEW");

        when(violationRepository.save(any(Violation.class))).thenAnswer(i -> i.getArgument(0));

        Violation result = violationService.createViolation(violation);

        assertEquals("PENDING_REVIEW", result.getStatus());
    }

    @Test
    void getViolation_shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(violationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> violationService.getViolation(id));
    }

    @Test
    void getViolation_shouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        Violation violation = new Violation();
        violation.setId(id);
        violation.setCameraId("cam-001");
        violation.setViolationType("OVERSPEEDING");
        violation.setViolatedAt(Instant.now());
        violation.setStatus("DETECTED");

        when(violationRepository.findById(id)).thenReturn(Optional.of(violation));
        when(evidenceRepository.findByViolationId(id.toString())).thenReturn(List.of());

        ViolationResponse response = violationService.getViolation(id);

        assertEquals(id, response.getId());
        assertEquals("OVERSPEEDING", response.getViolationType());
        assertEquals("DETECTED", response.getStatus());
    }

    @Test
    void updateStatus_shouldCreateReviewAndUpdateViolation() {
        UUID id = UUID.randomUUID();
        Violation violation = new Violation();
        violation.setId(id);
        violation.setCameraId("cam-001");
        violation.setViolationType("OVERSPEEDING");
        violation.setStatus("PENDING_REVIEW");
        violation.setViolatedAt(Instant.now());

        when(violationRepository.findById(id)).thenReturn(Optional.of(violation));
        when(reviewRepository.save(any(ViolationReview.class))).thenAnswer(i -> i.getArgument(0));
        when(violationRepository.save(any(Violation.class))).thenAnswer(i -> i.getArgument(0));
        when(evidenceRepository.findByViolationId(id.toString())).thenReturn(List.of());

        ViolationResponse response = violationService.updateStatus(
                id, "CONFIRMED", "Evidence verified", "reviewer-1");

        assertEquals("CONFIRMED", response.getStatus());
        verify(reviewRepository).save(any(ViolationReview.class));
    }

    @Test
    void getViolations_shouldReturnPaginatedResults() {
        Violation v1 = new Violation();
        v1.setId(UUID.randomUUID());
        v1.setCameraId("cam-001");
        v1.setViolationType("OVERSPEEDING");
        v1.setViolatedAt(Instant.now());
        v1.setStatus("DETECTED");

        PageRequest pageable = PageRequest.of(0, 10);
        Page<Violation> page = new PageImpl<>(List.of(v1), pageable, 1);

        when(violationRepository.findAll(pageable)).thenReturn(page);
        when(evidenceRepository.findByViolationIdIn(anyList())).thenReturn(List.of());

        Page<ViolationResponse> result = violationService.getViolations(null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("OVERSPEEDING", result.getContent().get(0).getViolationType());
    }

    @Test
    void getViolations_shouldFilterByStatus() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Violation> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(violationRepository.findByStatus("DETECTED", pageable)).thenReturn(emptyPage);

        Page<ViolationResponse> result = violationService.getViolations("DETECTED", null, null, pageable);

        assertEquals(0, result.getTotalElements());
        verify(violationRepository).findByStatus("DETECTED", pageable);
    }

    @Test
    void getEvidence_shouldReturnList() {
        UUID violationId = UUID.randomUUID();
        ViolationEvidence evidence = new ViolationEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setViolationId(violationId.toString());
        evidence.setEvidenceType("FRAME");

        when(evidenceRepository.findByViolationId(violationId.toString())).thenReturn(List.of(evidence));

        var result = violationService.getEvidence(violationId);

        assertEquals(1, result.size());
        assertEquals("FRAME", result.get(0).getEvidenceType());
    }

    @Test
    void addEvidence_shouldSaveAndReturn() {
        ViolationEvidence evidence = new ViolationEvidence();
        evidence.setViolationId(UUID.randomUUID().toString());
        evidence.setEvidenceType("FRAME");

        when(evidenceRepository.save(any(ViolationEvidence.class))).thenReturn(evidence);

        var result = violationService.addEvidence(evidence);

        assertNotNull(result);
        verify(evidenceRepository).save(evidence);
    }
}
