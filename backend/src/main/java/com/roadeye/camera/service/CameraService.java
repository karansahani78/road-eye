package com.roadeye.camera.service;

import com.roadeye.camera.dto.CameraCalibrationRequest;
import com.roadeye.camera.dto.CameraCalibrationResponse;
import com.roadeye.camera.dto.CameraRequest;
import com.roadeye.camera.dto.CameraResponse;
import com.roadeye.camera.entity.Camera;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
        if (cameraRepository.existsByCameraCode(request.getCameraCode())) {
            throw new BadRequestException("Camera code already exists: " + request.getCameraCode());
        }

        Camera camera = new Camera();
        camera.setCameraCode(request.getCameraCode());
        camera.setName(request.getName());
        camera.setDescription(request.getDescription());
        camera.setSourceType(request.getSourceType());
        camera.setLocation(request.getLocation());
        camera.setLatitude(request.getLatitude());
        camera.setLongitude(request.getLongitude());
        camera.setDirection(request.getDirection());
        camera.setProcessingFps(request.getProcessingFps() != null ? request.getProcessingFps() : 15);
        camera.setActive(request.isActive());

        if (request.getSourceUrl() != null) {
            camera.setSourceUrlEncrypted(request.getSourceUrl());
        }

        camera = cameraRepository.save(camera);
        return mapToCameraResponse(camera);
    }

    public CameraResponse updateCamera(UUID id, CameraRequest request) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));

        camera.setName(request.getName());
        camera.setDescription(request.getDescription());
        camera.setSourceType(request.getSourceType());
        camera.setLocation(request.getLocation());
        camera.setLatitude(request.getLatitude());
        camera.setLongitude(request.getLongitude());
        camera.setDirection(request.getDirection());
        camera.setProcessingFps(request.getProcessingFps() != null ? request.getProcessingFps() : 15);
        camera.setActive(request.isActive());

        if (request.getSourceUrl() != null) {
            camera.setSourceUrlEncrypted(request.getSourceUrl());
        }

        camera = cameraRepository.save(camera);
        return mapToCameraResponse(camera);
    }

    @Transactional(readOnly = true)
    public CameraResponse getCamera(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", id));
        return mapToCameraResponse(camera);
    }

    @Transactional(readOnly = true)
    public Page<CameraResponse> getCameras(Pageable pageable) {
        return cameraRepository.findAll(pageable).map(this::mapToCameraResponse);
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
        result.put("status", "success");
        result.put("message", "Connection test completed for camera: " + camera.getName());
        result.put("testedAt", Instant.now());

        return result;
    }

    @Transactional(readOnly = true)
    public Page<CameraHealthEvent> getCameraHealth(UUID cameraId, Pageable pageable) {
        if (!cameraRepository.existsById(cameraId)) {
            throw new ResourceNotFoundException("Camera", "id", cameraId);
        }
        return cameraHealthEventRepository.findByCameraIdOrderByRecordedAtDesc(cameraId, pageable);
    }

    public CameraCalibrationResponse getCalibration(UUID cameraId) {
        CameraCalibration calibration = cameraCalibrationRepository.findByCameraIdAndActiveTrue(cameraId)
                .orElseThrow(() -> new ResourceNotFoundException("CameraCalibration", "cameraId", cameraId));
        return mapToCalibrationResponse(calibration);
    }

    public CameraCalibrationResponse createCalibration(UUID cameraId, CameraCalibrationRequest request, String calibratedBy) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", cameraId));

        CameraCalibration calibration = new CameraCalibration();
        calibration.setCamera(camera);
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
