package com.roadeye.incident.controller;

import com.roadeye.incident.dto.IncidentCreateRequest;
import com.roadeye.incident.dto.IncidentResponse;
import com.roadeye.incident.dto.IncidentUpdateRequest;
import com.roadeye.incident.dto.AssignRequest;
import com.roadeye.incident.service.IncidentService;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<IncidentResponse>>> getIncidents(
            @RequestParam(required = false) String cameraId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        Page<IncidentResponse> page = incidentService.getIncidents(cameraId, type, status, severity, pageable);
        PagedResponse<IncidentResponse> response = PagedResponse.<IncidentResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(incidentService.getIncident(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'NATIONAL_ADMIN', 'CONTROL_ROOM_OPERATOR')")
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(@RequestBody IncidentCreateRequest request) {
        IncidentResponse created = incidentService.createFromRequest(request);
        return ResponseEntity.ok(ApiResponse.success(created, "Incident created"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'NATIONAL_ADMIN', 'CONTROL_ROOM_OPERATOR', 'TRAFFIC_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateIncident(
            @PathVariable UUID id, @RequestBody IncidentUpdateRequest request) {
        IncidentResponse updated = incidentService.updateIncident(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Incident updated"));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'NATIONAL_ADMIN', 'CONTROL_ROOM_OPERATOR')")
    public ResponseEntity<ApiResponse<IncidentResponse>> assignIncident(
            @PathVariable UUID id, @RequestBody AssignRequest request, Authentication auth) {
        String assignee = request.getAssignee() != null && !request.getAssignee().isBlank() ? request.getAssignee() : auth.getName();
        IncidentResponse updated = incidentService.assignIncident(id, assignee);
        return ResponseEntity.ok(ApiResponse.success(updated, "Incident assigned to " + assignee));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'NATIONAL_ADMIN', 'CONTROL_ROOM_OPERATOR', 'TRAFFIC_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveIncident(
            @PathVariable UUID id, @RequestBody(required = false) String notes) {
        IncidentResponse updated = incidentService.resolveIncident(id, notes);
        return ResponseEntity.ok(ApiResponse.success(updated, "Incident resolved"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of(
                "active", incidentService.countActive(),
                "critical", incidentService.countBySeverity("CRITICAL"),
                "high", incidentService.countBySeverity("HIGH"),
                "medium", incidentService.countBySeverity("MEDIUM"),
                "low", incidentService.countBySeverity("LOW")
        )));
    }
}
