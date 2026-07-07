package com.roadeye.violation.controller;

import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import com.roadeye.violation.dto.ViolationResponse;
import com.roadeye.violation.entity.ViolationEvidence;
import com.roadeye.violation.service.ViolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/violations")
public class ViolationController {

    private final ViolationService violationService;

    public ViolationController(ViolationService violationService) {
        this.violationService = violationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ViolationResponse>>> getViolations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cameraId,
            Pageable pageable) {
        Page<ViolationResponse> page = violationService.getViolations(status, type, cameraId, pageable);
        PagedResponse<ViolationResponse> response = PagedResponse.<ViolationResponse>builder()
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
    public ResponseEntity<ApiResponse<ViolationResponse>> getViolation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(violationService.getViolation(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ViolationResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest request,
            Authentication auth) {
        String reviewer = auth != null ? auth.getName() : "system";
        ViolationResponse updated = violationService.updateStatus(id, request.getStatus(), request.getComment(), reviewer);
        return ResponseEntity.ok(ApiResponse.success(updated, "Status updated to " + request.getStatus()));
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<List<ViolationEvidence>>> getEvidence(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(violationService.getEvidence(id)));
    }

    static class StatusUpdateRequest {
        private String status;
        private String comment;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
