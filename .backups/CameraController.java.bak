package com.roadeye.camera.controller;

import com.roadeye.camera.dto.CameraCalibrationRequest;
import com.roadeye.camera.dto.CameraCalibrationResponse;
import com.roadeye.camera.dto.CameraHealthEventResponse;
import com.roadeye.camera.dto.CameraRequest;
import com.roadeye.camera.dto.CameraResponse;
import com.roadeye.camera.service.CameraEventService;
import com.roadeye.camera.service.CameraService;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraController {

    private static final Logger log = LoggerFactory.getLogger(CameraController.class);

    private final CameraService cameraService;
    private final CameraEventService cameraEventService;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public CameraController(CameraService cameraService,
                            CameraEventService cameraEventService,
                            RestTemplate restTemplate) {
        this.cameraService = cameraService;
        this.cameraEventService = cameraEventService;
        this.restTemplate = restTemplate;
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
    public ResponseEntity<ApiResponse<PagedResponse<CameraHealthEventResponse>>> getCameraHealth(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        Page<CameraHealthEventResponse> healthPage = cameraService.getCameraHealth(id, pageable);

        PagedResponse<CameraHealthEventResponse> pagedResponse = PagedResponse.<CameraHealthEventResponse>builder()
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
        CameraCalibrationResponse response = cameraService.getCalibration(id).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/calibration")
    public ResponseEntity<ApiResponse<CameraCalibrationResponse>> createCalibration(
            @PathVariable UUID id,
            @Valid @RequestBody CameraCalibrationRequest request,
            Authentication authentication) {
        CameraCalibrationResponse response = cameraService.createCalibration(id, request, authentication.getName());

        // Push calibration to AI Engine if stream is running (best-effort)
        try {
            String calibrateUrl = aiServiceUrl + "/api/v1/streams/" + id + "/calibrate";
            Map<String, Object> calBody = new HashMap<>();
            calBody.put("known_distance_m", request.getKnownDistance());
            calBody.put("calibration_data", request.getCalibrationData());
            calBody.put("reference_point1", request.getReferencePoint1());
            calBody.put("reference_point2", request.getReferencePoint2());
            HttpEntity<Map<String, Object>> calRequest = new HttpEntity<>(calBody);
            restTemplate.postForEntity(calibrateUrl, calRequest, String.class);
        } catch (Exception e) {
            // Calibration saved to DB; AI Engine push is best-effort
            log.warn("Failed to push calibration to AI Engine for camera {}: {}", id, e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/stream/start")
    public ResponseEntity<ApiResponse<String>> startStream(@PathVariable UUID id) {
        var camera = cameraService.getCameraEntity(id);
        try {
            cameraEventService.startCameraStream(camera);
            return ResponseEntity.ok(ApiResponse.success("Stream started"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("502") || msg.contains("Unable to connect")) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(ApiResponse.error("Unable to connect to camera stream"));
            }
            if (msg.contains("409") || msg.contains("already running")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("Stream already running for this camera"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to start camera stream"));
        }
    }

    @PostMapping("/{id}/stream/stop")
    public ResponseEntity<ApiResponse<String>> stopStream(@PathVariable UUID id) {
        var camera = cameraService.getCameraEntity(id);
        try {
            cameraEventService.stopCameraStream(camera);
            return ResponseEntity.ok(ApiResponse.success("Stream stopped"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to stop camera stream"));
        }
    }

    @GetMapping("/{id}/stream/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStreamStatus(@PathVariable UUID id) {
        try {
            String url = aiServiceUrl + "/api/v1/streams/" + id + "/status";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            // 404 from AI Engine means no stream — return STOPPED, not UNKNOWN
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "camera_id", id.toString(),
                    "stream", Map.of(
                            "status", "STOPPED",
                            "fps", 0,
                            "vehicles_detected", 0,
                            "frame_count", 0,
                            "dropped_frames", 0
                    )
            )));
        }
    }

    @GetMapping(value = "/{id}/stream/mjpeg", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> proxyMjpeg(@PathVariable UUID id) {
        String url = aiServiceUrl + "/api/v1/streams/" + id + "/mjpeg";
        StreamingResponseBody body = outputStream -> {
            try (var in = URI.create(url).toURL().openStream()) {
                in.transferTo(outputStream);
                outputStream.flush();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("multipart/x-mixed-replace; boundary=frame"))
                .body(body);
    }

    @GetMapping("/{id}/stream/frame")
    public ResponseEntity<byte[]> proxyFrame(@PathVariable UUID id) {
        try {
            String url = aiServiceUrl + "/api/v1/streams/" + id + "/frame";
            byte[] frameBytes = restTemplate.getForObject(url, byte[].class);
            if (frameBytes == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(frameBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
