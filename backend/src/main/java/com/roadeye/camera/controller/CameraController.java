package com.roadeye.camera.controller;

import com.roadeye.camera.dto.CameraCalibrationRequest;
import com.roadeye.camera.dto.CameraCalibrationResponse;
import com.roadeye.camera.dto.CameraHealthEventResponse;
import com.roadeye.camera.dto.CameraRequest;
import com.roadeye.camera.dto.CameraResponse;
import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.service.CameraEventService;
import com.roadeye.camera.service.CameraService;
import com.roadeye.camera.service.StreamStateCache;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraController {

    private static final Logger log = LoggerFactory.getLogger(CameraController.class);

    private final CameraService cameraService;
    private final CameraEventService cameraEventService;
    private final StreamStateCache streamStateCache;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public CameraController(CameraService cameraService,
                            CameraEventService cameraEventService,
                            StreamStateCache streamStateCache) {
        this.cameraService = cameraService;
        this.cameraEventService = cameraEventService;
        this.streamStateCache = streamStateCache;
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

    @PatchMapping("/{id}/source-url")
    public ResponseEntity<ApiResponse<CameraResponse>> updateSourceUrl(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String sourceUrl = body.get("sourceUrl");
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("sourceUrl is required"));
        }
        CameraResponse response = cameraService.updateSourceUrl(id, sourceUrl);
        log.info("Source URL updated for camera {}: source_type={}", id, response.getSourceType());
        return ResponseEntity.ok(ApiResponse.success(response));
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
        // 1. Save calibration to DB (fast, synchronous)
        CameraCalibrationResponse response = cameraService.createCalibration(id, request, authentication.getName());

        // 2. Push calibration to AI Engine asynchronously (fire-and-forget)
        cameraEventService.pushCalibrationAsync(id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/stream/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startStream(@PathVariable UUID id) {
        var camera = cameraService.getCameraEntity(id);

        // If already running, return immediately
        if (streamStateCache.isRunning(id.toString())) {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "status", "already_running",
                    "camera_id", id.toString(),
                    "stream", streamStateCache.getFullState(id.toString()).get("stream")
            )));
        }

        // Mark as STARTING immediately
        streamStateCache.markStarting(id.toString());
        cameraService.updateCameraHealth(id, Camera.HealthStatus.CONNECTING);

        // Fire-and-forget: start stream asynchronously via AI Engine
        cameraEventService.startCameraStreamAsync(camera);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "starting",
                "camera_id", id.toString(),
                "message", "Stream start initiated. Check status for readiness."
        )));
    }

    @PostMapping("/{id}/stream/stop")
    public ResponseEntity<ApiResponse<String>> stopStream(@PathVariable UUID id) {
        var camera = cameraService.getCameraEntity(id);
        streamStateCache.markStopped(id.toString());
        cameraService.updateCameraHealth(id, Camera.HealthStatus.OFFLINE);

        // Fire-and-forget stop
        cameraEventService.stopCameraStreamAsync(camera);

        return ResponseEntity.ok(ApiResponse.success("Stream stopped"));
    }

    @GetMapping("/{id}/stream/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStreamStatus(@PathVariable UUID id) {
        // Fast: read from backend-owned cache, no AI Engine call
        Map<String, Object> state = streamStateCache.getFullState(id.toString());
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    @GetMapping(value = "/{id}/stream/mjpeg", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> proxyMjpeg(@PathVariable UUID id) {
        String url = aiServiceUrl + "/api/v1/streams/" + id + "/mjpeg";
        StreamingResponseBody body = outputStream -> {
            try (var in = URI.create(url).toURL().openStream()) {
                in.transferTo(outputStream);
                outputStream.flush();
            } catch (IOException e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("Broken pipe") || msg.contains("Connection reset") || msg.contains("closed")) {
                    log.debug("MJPEG stream closed for camera {}: {}", id, msg);
                } else {
                    log.warn("MJPEG stream error for camera {}: {}", id, msg);
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("multipart/x-mixed-replace; boundary=frame"))
                .body(body);
    }
}
