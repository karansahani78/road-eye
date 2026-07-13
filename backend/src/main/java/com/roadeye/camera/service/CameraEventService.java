package com.roadeye.camera.service;

import com.roadeye.camera.dto.CameraCalibrationRequest;
import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.entity.CameraCalibration;
import com.roadeye.camera.entity.CameraHealthEvent;
import com.roadeye.camera.repository.CameraCalibrationRepository;
import com.roadeye.camera.repository.CameraHealthEventRepository;
import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.shared.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CameraEventService {

    private static final Logger log = LoggerFactory.getLogger(CameraEventService.class);

    private final CameraRepository cameraRepository;
    private final CameraCalibrationRepository cameraCalibrationRepository;
    private final CameraHealthEventRepository healthEventRepository;
    private final CameraService cameraService;
    private final RestTemplate restTemplate;
    private final EncryptionService encryptionService;
    private final StreamStateCache streamStateCache;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public CameraEventService(CameraRepository cameraRepository,
                              CameraCalibrationRepository cameraCalibrationRepository,
                              CameraHealthEventRepository healthEventRepository,
                              CameraService cameraService,
                              RestTemplate restTemplate,
                              EncryptionService encryptionService,
                              StreamStateCache streamStateCache) {
        this.cameraRepository = cameraRepository;
        this.cameraCalibrationRepository = cameraCalibrationRepository;
        this.healthEventRepository = healthEventRepository;
        this.cameraService = cameraService;
        this.restTemplate = restTemplate;
        this.encryptionService = encryptionService;
        this.streamStateCache = streamStateCache;
    }

    @Async
    public void startCameraStreamAsync(Camera camera) {
        String cameraId = camera.getId().toString();
        try {
            streamStateCache.markConnecting(cameraId);
            startCameraStream(camera);
            streamStateCache.markRunning(cameraId, null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            log.error("Async stream start failed for camera {}: {}", cameraId, msg);
            streamStateCache.markError(cameraId, msg);
            try {
                cameraService.updateCameraHealth(camera.getId(), Camera.HealthStatus.ERROR);
            } catch (Exception ex) {
                log.warn("Failed to update camera health status: {}", ex.getMessage());
            }
        }
    }

    @Async
    public void stopCameraStreamAsync(Camera camera) {
        String cameraId = camera.getId().toString();
        try {
            stopCameraStream(camera);
        } catch (Exception e) {
            log.error("Async stream stop failed for camera {}: {}", cameraId, e.getMessage());
        }
    }

    @Async
    public void pushCalibrationAsync(UUID cameraId, CameraCalibrationRequest request) {
        try {
            // Check if stream is running before pushing
            if (!streamStateCache.isRunning(cameraId.toString())) {
                log.info("Calibration saved for camera {}. Stream not running, will load on next start.", cameraId);
                return;
            }

            String calibrateUrl = aiServiceUrl + "/api/v1/streams/" + cameraId + "/calibrate";
            Map<String, Object> calBody = new HashMap<>();
            calBody.put("known_distance_m", request.getKnownDistance());
            calBody.put("calibration_data", request.getCalibrationData());
            calBody.put("reference_point1", request.getReferencePoint1());
            calBody.put("reference_point2", request.getReferencePoint2());
            HttpEntity<Map<String, Object>> calRequest = new HttpEntity<>(calBody, createJsonHeaders());
            restTemplate.postForEntity(calibrateUrl, calRequest, String.class);
            log.info("Calibration pushed to AI Engine for camera {}", cameraId);
        } catch (Exception e) {
            log.warn("Failed to push calibration to AI Engine for camera {}: {}", cameraId, e.getMessage());
        }
    }

    public void startCameraStream(Camera camera) {
        try {
            String url = aiServiceUrl + "/api/v1/streams/start";

            String decryptedUrl = encryptionService.decrypt(camera.getSourceUrlEncrypted());

            Map<String, Object> body = new HashMap<>();
            body.put("camera_id", camera.getId().toString());
            body.put("source_url", decryptedUrl);
            body.put("fps", camera.getProcessingFps() != null ? camera.getProcessingFps() : 15);

            // Include calibration if available
            Optional<CameraCalibration> calibration = cameraCalibrationRepository.findByCameraIdAndActiveTrue(camera.getId());
            if (calibration.isPresent()) {
                CameraCalibration cal = calibration.get();
                body.put("known_distance_m", cal.getKnownDistance());
                Double referencePixels = computeReferencePixels(cal.getReferencePoint1(), cal.getReferencePoint2());
                if (referencePixels != null) {
                    body.put("reference_pixels", referencePixels);
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, createJsonHeaders());
            restTemplate.postForEntity(url, request, String.class);

            log.info("Requested AI stream start for camera {}", camera.getId());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("AI Engine returned {} for camera {}: {}", e.getStatusCode(), camera.getId(), errorBody);
            String classification = classifyAiEngineError(e.getStatusCode().value(), errorBody);
            throw new RuntimeException(e.getStatusCode().value() + ": " + classification + ": " + errorBody, e);
        } catch (Exception e) {
            log.error("Failed to start AI stream for camera {}: {}", camera.getId(), e.getMessage());
            throw new RuntimeException("Failed to start camera stream: " + e.getMessage(), e);
        }
    }

    private String classifyAiEngineError(int statusCode, String errorBody) {
        if (errorBody == null) return "UNKNOWN";
        String lower = errorBody.toLowerCase();
        if (lower.contains("auth") || lower.contains("401") || lower.contains("unauthorized")) {
            return "Camera authentication failed";
        }
        if (lower.contains("unreachable") || lower.contains("refused") || lower.contains("timeout")) {
            return "Camera host unreachable";
        }
        if (lower.contains("not found") || lower.contains("404")) {
            return "Camera stream not found";
        }
        return "AI Engine error (" + statusCode + ")";
    }

    private Double computeReferencePixels(String point1, String point2) {
        if (point1 == null || point2 == null) return null;
        try {
            String[] p1 = point1.split(",");
            String[] p2 = point2.split(",");
            if (p1.length >= 2 && p2.length >= 2) {
                double x1 = Double.parseDouble(p1[0].trim());
                double y1 = Double.parseDouble(p1[1].trim());
                double x2 = Double.parseDouble(p2[0].trim());
                double y2 = Double.parseDouble(p2[1].trim());
                double dx = x2 - x1;
                double dy = y2 - y1;
                return Math.sqrt(dx * dx + dy * dy);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse reference points: {} / {}", point1, point2);
        }
        return null;
    }

    public void stopCameraStream(Camera camera) {
        try {
            String url = aiServiceUrl + "/api/v1/streams/stop";

            Map<String, Object> body = Map.of(
                    "camera_id", camera.getId().toString()
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, createJsonHeaders());
            restTemplate.postForEntity(url, request, String.class);

            log.info("Requested AI stream stop for camera {}", camera.getId());
        } catch (Exception e) {
            log.error("Failed to stop AI stream for camera {}: {}", camera.getId(), e.getMessage());
        }
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Scheduled(fixedRate = 300000)
    public void checkCameraHealth() {
        List<Camera> activeCameras = cameraRepository.findByActiveTrue();
        Instant now = Instant.now();
        for (Camera camera : activeCameras) {
            CameraHealthEvent event = new CameraHealthEvent();
            event.setCamera(camera);
            event.setStatus(camera.getHealthStatus().name());
            event.setMessage("Scheduled health snapshot");
            event.setRecordedAt(now);
            healthEventRepository.save(event);
        }
        log.info("Health snapshot recorded for {} cameras", activeCameras.size());
    }
}
