package com.roadeye.trafficmap.service;

import com.roadeye.alert.entity.Alert;
import com.roadeye.alert.repository.AlertRepository;
import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.speed.repository.SpeedMeasurementRepository;
import com.roadeye.trafficmap.dto.MapAlertDTO;
import com.roadeye.trafficmap.dto.MapCameraDTO;
import com.roadeye.trafficmap.dto.MapViolationDTO;
import com.roadeye.trafficmap.dto.TrafficMapResponse;
import com.roadeye.vehicle.repository.VehicleTrackRepository;
import com.roadeye.violation.entity.Violation;
import com.roadeye.violation.repository.ViolationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrafficMapService {

    private final CameraRepository cameraRepository;
    private final VehicleTrackRepository vehicleTrackRepository;
    private final SpeedMeasurementRepository speedMeasurementRepository;
    private final ViolationRepository violationRepository;
    private final AlertRepository alertRepository;

    public TrafficMapService(CameraRepository cameraRepository,
                             VehicleTrackRepository vehicleTrackRepository,
                             SpeedMeasurementRepository speedMeasurementRepository,
                             ViolationRepository violationRepository,
                             AlertRepository alertRepository) {
        this.cameraRepository = cameraRepository;
        this.vehicleTrackRepository = vehicleTrackRepository;
        this.speedMeasurementRepository = speedMeasurementRepository;
        this.violationRepository = violationRepository;
        this.alertRepository = alertRepository;
    }

    public TrafficMapResponse getTrafficMap(int minutes) {
        Instant since = Instant.now().minusSeconds(minutes * 60L);

        List<Camera> allCameras = cameraRepository.findByActiveTrue();
        List<Camera> mappedCameras = cameraRepository.findByLatitudeIsNotNullAndLongitudeIsNotNullAndActiveTrue();

        Map<String, Camera> cameraMap = mappedCameras.stream()
                .collect(Collectors.toMap(c -> c.getId().toString(), Function.identity()));

        long totalCameras = allCameras.size();
        long mappedCount = mappedCameras.size();
        long unmappedCount = cameraRepository.countByLatitudeIsNullAndActiveTrue();
        long onlineCount = cameraRepository.countByHealthStatus(Camera.HealthStatus.ONLINE);
        long offlineCount = cameraRepository.countByHealthStatus(Camera.HealthStatus.OFFLINE);
        long degradedCount = cameraRepository.countByHealthStatus(Camera.HealthStatus.DEGRADED);

        long currentActiveVehicles = vehicleTrackRepository.countDistinctActiveTracksSince(since);
        long activeViolations = violationRepository.countByStatus("DETECTED");
        long unacknowledgedAlerts = alertRepository.countByAcknowledgedFalse();

        List<MapCameraDTO> cameraDTOs = mappedCameras.stream()
                .map(cam -> buildCameraDTO(cam, since))
                .collect(Collectors.toList());

        List<MapViolationDTO> violationDTOs = violationRepository.findRecentViolations(since)
                .stream()
                .map(v -> buildViolationDTO(v, cameraMap))
                .limit(100)
                .collect(Collectors.toList());

        List<MapAlertDTO> alertDTOs = alertRepository.findActiveAlerts()
                .stream()
                .map(a -> buildAlertDTO(a, cameraMap))
                .limit(100)
                .collect(Collectors.toList());

        TrafficMapResponse.SummaryDTO summary = TrafficMapResponse.SummaryDTO.builder()
                .totalCameras((int) totalCameras)
                .mappedCameras((int) mappedCount)
                .unmappedCameras((int) unmappedCount)
                .onlineCameras((int) onlineCount)
                .offlineCameras((int) offlineCount)
                .degradedCameras((int) degradedCount)
                .activeStreams((int) mappedCameras.stream().filter(c -> c.getHealthStatus() == Camera.HealthStatus.ONLINE).count())
                .currentActiveVehicles((int) currentActiveVehicles)
                .activeViolations((int) activeViolations)
                .unacknowledgedAlerts((int) unacknowledgedAlerts)
                .build();

        return TrafficMapResponse.builder()
                .summary(summary)
                .cameras(cameraDTOs)
                .violations(violationDTOs)
                .alerts(alertDTOs)
                .build();
    }

    private MapCameraDTO buildCameraDTO(Camera cam, Instant since) {
        int vehiclesLast15Min = (int) vehicleTrackRepository.countByCameraIdAndLastSeenAfter(
                cam.getId().toString(), since);

        String streamStatus = cam.getHealthStatus() == Camera.HealthStatus.ONLINE ? "RUNNING" : "STOPPED";
        int recentViolations = (int) violationRepository.countByCameraIdAndViolatedAtAfter(
                cam.getId().toString(), since);
        int recentAlerts = (int) alertRepository.countByCameraIdAndCreatedAtAfter(
                cam.getId().toString(), since);

        String congestionLevel = classifyCongestion(vehiclesLast15Min);

        Double avgSpeed = speedMeasurementRepository
                .getAverageSpeedKmhByCameraSince(cam.getId().toString(), since)
                .orElse(null);
        boolean speedAvailable = avgSpeed != null && avgSpeed > 0;

        return MapCameraDTO.builder()
                .id(cam.getId())
                .cameraCode(cam.getCameraCode())
                .name(cam.getName())
                .latitude(cam.getLatitude())
                .longitude(cam.getLongitude())
                .healthStatus(cam.getHealthStatus().name())
                .streamStatus(streamStatus)
                .location(cam.getLocation())
                .vehiclesLast15Min(vehiclesLast15Min)
                .averageSpeedKmh(speedAvailable ? avgSpeed : null)
                .speedAvailable(speedAvailable)
                .congestionLevel(congestionLevel)
                .recentViolations(recentViolations)
                .recentAlerts(recentAlerts)
                .lastHeartbeat(cam.getLastHeartbeat())
                .build();
    }

    private MapViolationDTO buildViolationDTO(Violation v, Map<String, Camera> cameraMap) {
        Camera cam = cameraMap.get(v.getCameraId());
        return MapViolationDTO.builder()
                .id(v.getId())
                .violationType(v.getViolationType())
                .cameraId(v.getCameraId())
                .cameraName(cam != null ? cam.getName() : null)
                .latitude(cam != null ? cam.getLatitude() : null)
                .longitude(cam != null ? cam.getLongitude() : null)
                .status(v.getStatus())
                .confidence(v.getConfidence())
                .vehicleCategory(v.getVehicleCategory())
                .plateText(v.getPlateText())
                .speedKmh(v.getSpeedKmh())
                .timestamp(v.getViolatedAt())
                .build();
    }

    private MapAlertDTO buildAlertDTO(Alert a, Map<String, Camera> cameraMap) {
        Camera cam = a.getCameraId() != null ? cameraMap.get(a.getCameraId()) : null;
        return MapAlertDTO.builder()
                .id(a.getId())
                .type(a.getType())
                .severity(a.getSeverity())
                .cameraId(a.getCameraId())
                .cameraName(cam != null ? cam.getName() : null)
                .latitude(cam != null ? cam.getLatitude() : null)
                .longitude(cam != null ? cam.getLongitude() : null)
                .message(a.getMessage())
                .acknowledged(a.isAcknowledged())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private String classifyCongestion(int vehiclesInWindow) {
        if (vehiclesInWindow <= 0) return "UNKNOWN";
        if (vehiclesInWindow <= 5) return "FREE_FLOW";
        if (vehiclesInWindow <= 20) return "LOW";
        if (vehiclesInWindow <= 50) return "MODERATE";
        if (vehiclesInWindow <= 100) return "HEAVY";
        return "CONGESTED";
    }
}
