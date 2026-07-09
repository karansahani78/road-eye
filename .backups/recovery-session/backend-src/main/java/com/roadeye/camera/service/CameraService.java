package com.roadeye.camera.service;

import com.roadeye.camera.dto.CameraCalibrationRequest;
import com.roadeye.camera.dto.CameraCalibrationResponse;
import com.roadeye.camera.dto.CameraHealthEventResponse;
import com.roadeye.camera.dto.CameraRequest;
import com.roadeye.camera.dto.CameraResponse;
import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.entity.Camera.LocationSource;
import com.roadeye.camera.entity.Camera.SourceType;
import com.roadeye.camera.entity.CameraCalibration;
import com.roadeye.camera.entity.CameraHealthEvent;
import com.roadeye.camera.repository.CameraCalibrationRepository;
import com.roadeye.camera.repository.CameraHealthEventRepository;
import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.shared.BadRequestException;
import com.roadeye.shared.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CameraService {

    private final CameraRepository cameraRepository;
    private final CameraCalibrationRepository cameraCalibrationRepository;
    private final CameraHealthEventRepository cameraHealthEventRepository;

    public CameraService(CameraRepository cameraRepository,
                         CameraCalibrationRepository cameraCalibrationRepository,
                         CameraHealthEventRepository cameraHealthEventRepository) {
        this.cameraRepository = cameraRepository;
        this.cameraCalibrationRepository = cameraCalibrationRepository;
        this.cameraHealthEventRepository = cameraHealthEventRepository;
    }

    public CameraResponse createCamera(CameraRequest request) {
        String trimmedCode = request.getCameraCode().trim();
        Optional<Camera> existingOpt = cameraRepository.findByCameraCode(trimmedCode);
        if (existingOpt.isPresent()) {
            Camera existing = existingOpt.get();
            if (existing.isActive()) {
                throw new BadRequestException("Camera code already exists: " + trimmedCode);
            }
            // Reactivate deactivated camera, preserving existing UUID and evidence references
            existing.setActive(true);
            existing.setName(request.getName());
            existing.setDescription(request.getDescription());
            existing.setSourceType(request.getSourceType());
            existing.setLocation(request.getLocation());
            existing.setLatitude(request.getLatitude());
            existing.setLongitude(request.getLongitude());
            existing.setDirection(request.getDirection());
            existing.setProcessingFps(request.getProcessingFps() != null ? request.getProcessingFps() : 15);
            existing.setLocationSource(request.getLocationSource());
            existing.setLocationAccuracyMeters(request.getLocationAccuracyMeters());
            existing.setHealthStatus(Camera.HealthStatus.UNKNOWN);

            if (StringUtils.hasText(request.getSourceUrl())) {
                existing.setSourceUrlEncrypted(request.getSourceUrl());
            }

            existing = cameraRepository.save(existing);
            return mapToCameraResponse(existing);
        }

        Camera camera = new Camera();
        camera.setCameraCode(trimmedCode);
        camera.setName(request.getName());
        camera.setDescription(request.getDescription());
        camera.setSourceType(request.getSourceType());
        camera.setLocation(request.getLocation());
        camera.setLatitude(request.getLatitude());
        camera.setLongitude(request.getLongitude());
        camera.setDirection(request.getDirection());
        camera.setProcessingFps(request.getProcessingFps() != null ? request.getProcessingFps() : 15);
        camera.setActive(request.isActive());
        camera.setLocationSource(request.getLocationSource());
        camera.setLocationAccuracyMeters(request.getLocationAccuracyMeters());

        if (StringUtils.hasText(request.getSourceUrl())) {
            camera.setSourceUrlEncrypted(request.getSourceUrl());
        }

        camera = cameraRepository.save(camera);
        return mapToCameraResponse(camera);
    }

    public CameraResponse updateCamera(UUID id, CameraRequest request) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        // Trim camera code to prevent whitespace-induced duplicates
        String trimmedCode = request.getCameraCode().trim();
        camera.setCameraCode(trimmedCode);
        camera.setName(request.getName());
        camera.setDescription(request.getDescription());
        camera.setSourceType(request.getSourceType());
        camera.setLocation(request.getLocation());
        camera.setLatitude(request.getLatitude());
        camera.setLongitude(request.getLongitude());
        camera.setDirection(request.getDirection());
        camera.setProcessingFps(request.getProcessingFps() != null ? request.getProcessingFps() : 15);
        camera.setActive(request.isActive());
        camera.setLocationSource(request.getLocationSource());
        camera.setLocationAccuracyMeters(request.getLocationAccuracyMeters());

        if (StringUtils.hasText(request.getSourceUrl())) {
            camera.setSourceUrlEncrypted(request.getSourceUrl());
        }

        camera = cameraRepository.save(camera);
        return mapToCameraResponse(camera);
    }

    @Transactional(readOnly = true)
    public CameraResponse getCamera(UUID id) {
        Camera camera = getCameraEntity(id);
        return mapToCameraResponse(camera);
    }

    public Camera getCameraEntity(UUID id) {
        return cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));
    }

    @Transactional(readOnly = true)
    public Page<CameraResponse> getCameras(Pageable pageable) {
        return cameraRepository.findAll(pageable).map(this::mapToCameraResponse);
    }

    public void updateCameraHealth(UUID id, Camera.HealthStatus status) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));
        camera.setHealthStatus(status);
        cameraRepository.save(camera);
    }

    public void deleteCamera(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));
        camera.setActive(false);
        cameraRepository.save(camera);
    }

    public Map<String, Object> testConnection(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        Map<String, Object> result = new HashMap<>();
        result.put("cameraId", id);
        result.put("cameraCode", camera.getCameraCode());
        result.put("sourceType", camera.getSourceType());

        String sourceUrl = camera.getSourceUrlEncrypted();
        String host = null;
        int port = -1;

        if (sourceUrl != null && !sourceUrl.isBlank()) {
            String sanitizedUrl = sanitizeSourceUrl(sourceUrl);
            try {
                URI uri = URI.create(sourceUrl);
                host = uri.getHost();
                port = uri.getPort();
                if (port < 0) port = switch (uri.getScheme()) {
                    case "rtsp" -> 554;
                    case "http", "https" -> uri.getScheme().equals("https") ? 443 : 80;
                    default -> -1;
                };
            } catch (IllegalArgumentException e) {
                result.put("status", "error");
                result.put("message", "Invalid URL: " + e.getMessage());
                result.put("testedAt", Instant.now());
                return result;
            }

            if (host != null && port > 0) {
                try (Socket s = new Socket(host, port)) {
                    s.setSoTimeout(5000);
                    result.put("status", "success");
                    result.put("message", "Reachable: " + host + ":" + port);
                } catch (IOException e) {
                    result.put("status", "unreachable");
                    result.put("message", host + ":" + port + " - " + e.getMessage());
                }
            } else {
                result.put("status", "unchecked");
                result.put("message", "Cannot parse host/port from: " + sanitizedUrl);
            }
        } else {
            result.put("status", "unconfigured");
            result.put("message", "No source URL configured for camera");
        }

        result.put("testedAt", Instant.now());
        return result;
    }

    private static String sanitizeSourceUrl(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                return url.replaceFirst(userInfo + "@", "****:****@");
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    @Transactional(readOnly = true)
    public Page<CameraHealthEventResponse> getCameraHealth(UUID cameraId, Pageable pageable) {
        if (!cameraRepository.existsById(cameraId)) {
            throw new ResourceNotFoundException("Camera", "id", cameraId);
        }
        return cameraHealthEventRepository.findByCameraIdOrderByRecordedAtDesc(cameraId, pageable)
                .map(this::mapToHealthEventResponse);
    }

    private CameraHealthEventResponse mapToHealthEventResponse(CameraHealthEvent event) {
        return CameraHealthEventResponse.builder()
                .id(event.getId())
                .cameraId(event.getCamera().getId())
                .cameraName(event.getCamera().getName())
                .status(event.getStatus())
                .message(event.getMessage())
                .errorType(event.getErrorType())
                .processedFrames(event.getProcessedFrames())
                .droppedFrames(event.getDroppedFrames())
                .inferenceLatencyMs(event.getInferenceLatencyMs())
                .recordedAt(event.getRecordedAt())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public Optional<CameraCalibrationResponse> getCalibration(UUID cameraId) {
        return cameraCalibrationRepository.findByCameraIdAndActiveTrue(cameraId)
                .map(this::mapToCalibrationResponse);
    }

    public CameraCalibrationResponse createCalibration(UUID cameraId, CameraCalibrationRequest request, String calibratedBy) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", cameraId));

        CameraCalibration calibration = cameraCalibrationRepository.findByCameraId(cameraId)
                .orElseGet(CameraCalibration::new);

        if (calibration.getId() == null) {
            calibration.setCamera(camera);
        }

        calibration.setCalibrationData(request.getCalibrationData());
        calibration.setReferencePoint1(request.getReferencePoint1());
        calibration.setReferencePoint2(request.getReferencePoint2());
        calibration.setKnownDistance(request.getKnownDistance());
        calibration.setAlgorithmVersion(request.getAlgorithmVersion());
        calibration.setCalibratedBy(calibratedBy);
        calibration.setCalibratedAt(Instant.now());
        calibration.setActive(true);

        calibration = cameraCalibrationRepository.save(calibration);
        return mapToCalibrationResponse(calibration);
    }

    private CameraResponse mapToCameraResponse(Camera camera) {
        return CameraResponse.builder()
                .id(camera.getId())
                .cameraCode(camera.getCameraCode())
                .name(camera.getName())
                .description(camera.getDescription())
                .sourceType(camera.getSourceType())
                .location(camera.getLocation())
                .latitude(camera.getLatitude())
                .longitude(camera.getLongitude())
                .direction(camera.getDirection())
                .processingFps(camera.getProcessingFps())
                .resolution(camera.getResolution())
                .active(camera.isActive())
                .healthStatus(camera.getHealthStatus())
                .lastHeartbeat(camera.getLastHeartbeat())
                .lastSuccessfulFrame(camera.getLastSuccessfulFrame())
                .locationSource(camera.getLocationSource())
                .locationAccuracyMeters(camera.getLocationAccuracyMeters())
                .sourceUrlConfigured(camera.getSourceUrlEncrypted() != null && !camera.getSourceUrlEncrypted().isBlank())
                .createdAt(camera.getCreatedAt())
                .updatedAt(camera.getUpdatedAt())
                .build();
    }

    private CameraCalibrationResponse mapToCalibrationResponse(CameraCalibration calibration) {
        return CameraCalibrationResponse.builder()
                .id(calibration.getId())
                .cameraId(calibration.getCamera().getId())
                .calibrationVersion(calibration.getCalibrationVersion())
                .calibrationData(calibration.getCalibrationData())
                .referencePoint1(calibration.getReferencePoint1())
                .referencePoint2(calibration.getReferencePoint2())
                .knownDistance(calibration.getKnownDistance())
                .perspectiveTransform(calibration.getPerspectiveTransform())
                .algorithmVersion(calibration.getAlgorithmVersion())
                .calibratedBy(calibration.getCalibratedBy())
                .calibratedAt(calibration.getCalibratedAt())
                .active(calibration.isActive())
                .createdAt(calibration.getCreatedAt())
                .updatedAt(calibration.getUpdatedAt())
                .build();
    }
}
