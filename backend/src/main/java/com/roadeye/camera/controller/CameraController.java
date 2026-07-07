package com.roadeye.camera.controller;

import com.roadeye.camera.dto.CameraCalibrationRequest;
import com.roadeye.camera.dto.CameraCalibrationResponse;
import com.roadeye.camera.dto.CameraRequest;
import com.roadeye.camera.dto.CameraResponse;
import com.roadeye.camera.entity.CameraHealthEvent;
import com.roadeye.camera.service.CameraService;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraController {

    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CameraResponse>> createCamera(@Valid @RequestBody CameraRequest request) {
        CameraResponse response = cameraService.createCamera(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CameraResponse>> updateCamera(@PathVariable UUID id,
                                                                    @Valid @RequestBody CameraRequest request) {
        CameraResponse response = cameraService.updateCamera(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CameraResponse>> getCamera(@PathVariable UUID id) {
        CameraResponse response = cameraService.getCamera(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CameraResponse>>> getCameras(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String sourceType) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CameraResponse> cameraPage = cameraService.getCameras(pageable);

        PagedResponse<CameraResponse> pagedResponse = PagedResponse.<CameraResponse>builder()
                .content(cameraPage.getContent())
                .page(cameraPage.getNumber())
                .size(cameraPage.getSize())
                .totalElements(cameraPage.getTotalElements())
                .totalPages(cameraPage.getTotalPages())
                .first(cameraPage.isFirst())
                .last(cameraPage.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCamera(@PathVariable UUID id) {
        cameraService.deleteCamera(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Camera deactivated successfully"));
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection(@PathVariable UUID id) {
        Map<String, Object> result = cameraService.testConnection(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<ApiResponse<PagedResponse<CameraHealthEvent>>> getCameraHealth(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        Page<CameraHealthEvent> healthPage = cameraService.getCameraHealth(id, pageable);

        PagedResponse<CameraHealthEvent> pagedResponse = PagedResponse.<CameraHealthEvent>builder()
                .content(healthPage.getContent())
                .page(healthPage.getNumber())
                .size(healthPage.getSize())
                .totalElements(healthPage.getTotalElements())
                .totalPages(healthPage.getTotalPages())
                .first(healthPage.isFirst())
                .last(healthPage.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @GetMapping("/{id}/calibration")
    public ResponseEntity<ApiResponse<CameraCalibrationResponse>> getCalibration(@PathVariable UUID id) {
        CameraCalibrationResponse response = cameraService.getCalibration(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/calibration")
    public ResponseEntity<ApiResponse<CameraCalibrationResponse>> createCalibration(
            @PathVariable UUID id,
            @Valid @RequestBody CameraCalibrationRequest request,
            Authentication authentication) {
        CameraCalibrationResponse response = cameraService.createCalibration(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
