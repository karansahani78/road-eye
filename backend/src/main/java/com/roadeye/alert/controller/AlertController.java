package com.roadeye.alert.controller;

import com.roadeye.alert.entity.Alert;
import com.roadeye.alert.service.AlertService;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<Alert>>> getAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String cameraId,
            @RequestParam(required = false) Boolean acknowledged,
            Pageable pageable) {
        Page<Alert> page = alertService.getAlerts(type, severity, cameraId, acknowledged, pageable);
        PagedResponse<Alert> response = PagedResponse.<Alert>builder()
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
    public ResponseEntity<ApiResponse<Alert>> getAlert(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(alertService.getAlert(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Alert>> createAlert(@RequestBody Alert alert) {
        Alert saved = alertService.createAlert(alert);
        return ResponseEntity.ok(ApiResponse.success(saved, "Alert created successfully"));
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<Alert>> acknowledgeAlert(@PathVariable UUID id, Authentication auth) {
        String user = auth != null ? auth.getName() : "system";
        Alert updated = alertService.acknowledgeAlert(id, user);
        return ResponseEntity.ok(ApiResponse.success(updated, "Alert acknowledged"));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Alert>> resolveAlert(@PathVariable UUID id) {
        Alert updated = alertService.resolveAlert(id);
        return ResponseEntity.ok(ApiResponse.success(updated, "Alert resolved"));
    }
}
