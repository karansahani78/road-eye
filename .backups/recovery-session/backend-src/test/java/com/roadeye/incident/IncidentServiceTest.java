package com.roadeye.incident;

import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.incident.dto.IncidentCreateRequest;
import com.roadeye.incident.dto.IncidentResponse;
import com.roadeye.incident.dto.IncidentUpdateRequest;
import com.roadeye.incident.entity.Incident;
import com.roadeye.incident.repository.IncidentRepository;
import com.roadeye.incident.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private CameraRepository cameraRepository;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(incidentRepository, cameraRepository);
    }

    @Test
    void createIncident_setsDefaults() {
        Incident input = new Incident();
        input.setCameraId("camera-1");
        input.setIncidentType("COLLISION");
        input.setSeverity("HIGH");

        Incident saved = new Incident();
        saved.setId(UUID.randomUUID());
        saved.setCameraId("camera-1");
        saved.setIncidentType("COLLISION");
        saved.setSeverity("HIGH");
        saved.setStatus("DETECTED");

        when(incidentRepository.save(any(Incident.class))).thenReturn(saved);

        Incident result = incidentService.createIncident(input);

        assertNotNull(result);
        verify(incidentRepository).save(input);
    }

    @Test
    void createIncident_setsDetectedAtWhenNull() {
        Incident input = new Incident();
        input.setCameraId("camera-1");
        input.setIncidentType("COLLISION");

        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Incident result = incidentService.createIncident(input);
        assertNotNull(result.getDetectedAt());
    }

    @Test
    void createFromRequest_mapsAllFields() {
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setCameraId("cam-1");
        request.setIncidentType("COLLISION");
        request.setSeverity("HIGH");
        request.setDescription("Test incident");
        request.setConfidence(0.95);
        request.setSource("AI_ENGINE");
        request.setOccurredAt(Instant.now());

        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        IncidentResponse response = incidentService.createFromRequest(request);
        assertNotNull(response);
        assertEquals("cam-1", response.getCameraId());
        assertEquals("COLLISION", response.getIncidentType());
        assertEquals("HIGH", response.getSeverity());
        assertEquals("DETECTED", response.getStatus());
        assertEquals(0.95, response.getConfidence());
        assertEquals("AI_ENGINE", response.getSource());
    }

    @Test
    void getIncident_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(com.roadeye.shared.ResourceNotFoundException.class,
                () -> incidentService.getIncident(id));
    }

    @Test
    void updateIncident_updatesStatus() {
        UUID id = UUID.randomUUID();
        Incident existing = new Incident();
        existing.setId(id);
        existing.setStatus("DETECTED");

        IncidentUpdateRequest update = new IncidentUpdateRequest();
        update.setStatus("ASSIGNED");

        when(incidentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponse response = incidentService.updateIncident(id, update);
        assertEquals("ASSIGNED", response.getStatus());
    }

    @Test
    void resolveIncident_setsResolvedAt() {
        UUID id = UUID.randomUUID();
        Incident existing = new Incident();
        existing.setId(id);
        existing.setStatus("ASSIGNED");

        when(incidentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponse response = incidentService.resolveIncident(id, "Issue resolved");
        assertEquals("RESOLVED", response.getStatus());
        assertNotNull(response.getResolvedAt());
    }

    @Test
    void assignIncident_setsAssignee() {
        UUID id = UUID.randomUUID();
        Incident existing = new Incident();
        existing.setId(id);
        existing.setStatus("DETECTED");

        when(incidentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponse response = incidentService.assignIncident(id, "officer-1");
        assertEquals("ASSIGNED", response.getStatus());
        assertEquals("officer-1", response.getAssignedTo());
        assertNotNull(response.getAssignedAt());
    }
}
