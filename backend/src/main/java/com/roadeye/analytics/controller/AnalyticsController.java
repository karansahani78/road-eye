package com.roadeye.analytics.controller;

import com.roadeye.alert.repository.AlertRepository;
import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.repository.CameraRepository;
import com.roadeye.shared.ApiResponse;
import com.roadeye.speed.repository.SpeedMeasurementRepository;
import com.roadeye.vehicle.repository.VehicleObservationRepository;
import com.roadeye.violation.repository.ViolationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final CameraRepository cameraRepository;
    private final ViolationRepository violationRepository;
    private final VehicleObservationRepository vehicleObservationRepository;
    private final AlertRepository alertRepository;
    private final SpeedMeasurementRepository speedMeasurementRepository;

    public AnalyticsController(CameraRepository cameraRepository,
                               ViolationRepository violationRepository,
                               VehicleObservationRepository vehicleObservationRepository,
                               AlertRepository alertRepository,
                               SpeedMeasurementRepository speedMeasurementRepository) {
        this.cameraRepository = cameraRepository;
        this.violationRepository = violationRepository;
        this.vehicleObservationRepository = vehicleObservationRepository;
        this.alertRepository = alertRepository;
        this.speedMeasurementRepository = speedMeasurementRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        Instant now = Instant.now();
        Instant todayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);

        long totalCameras = cameraRepository.count();
        long onlineCameras = cameraRepository.countByHealthStatus(Camera.HealthStatus.ONLINE);
        long offlineCameras = cameraRepository.countByHealthStatus(Camera.HealthStatus.OFFLINE);
        long activeStreams = cameraRepository.findByActiveTrue().size();
        long vehiclesDetected = vehicleObservationRepository.count();
        long trafficVolumeToday = vehicleObservationRepository.countByObservedAtBetween(todayStart, now);
        long activeViolations = violationRepository.countByStatus("PENDING_REVIEW")
                + violationRepository.countByStatus("UNDER_REVIEW")
                + violationRepository.countByStatus("NEEDS_ADDITIONAL_REVIEW")
                + violationRepository.countByStatus("CONFIRMED")
                + violationRepository.countByStatus("FORWARDED_TO_AUTHORITY");
        long pendingReviews = violationRepository.countByStatus("PENDING_REVIEW");
        long confirmedViolations = violationRepository.countByStatus("CONFIRMED");
        long activeCriticalAlerts = alertRepository.countBySeverityAndAcknowledged("CRITICAL", false);

        Optional<Double> avgSpeed = speedMeasurementRepository.getAverageSpeedKmh();
        double averageSpeed = avgSpeed.orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCameras", totalCameras);
        stats.put("onlineCameras", onlineCameras);
        stats.put("offlineCameras", offlineCameras);
        stats.put("activeStreams", activeStreams);
        stats.put("vehiclesDetected", vehiclesDetected);
        stats.put("trafficVolume", trafficVolumeToday);
        stats.put("averageSpeed", averageSpeed);
        stats.put("activeViolations", activeViolations);
        stats.put("pendingReviews", pendingReviews);
        stats.put("confirmedViolations", confirmedViolations);
        stats.put("activeCriticalAlerts", activeCriticalAlerts);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/traffic-volume")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTrafficVolume(
            @RequestParam(defaultValue = "7d") String period) {
        List<Map<String, Object>> result = new ArrayList<>();
        Instant now = Instant.now();
        Instant start = parsePeriod(period, now);

        Instant cursor = start;
        while (cursor.isBefore(now)) {
            Instant hourEnd = cursor.plus(Duration.ofHours(1));
            if (hourEnd.isAfter(now)) hourEnd = now;
            long count = vehicleObservationRepository.countByObservedAtBetween(cursor, hourEnd);
            Map<String, Object> point = new LinkedHashMap<>();
            String dateLabel = cursor.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString().substring(0, 10);
            point.put("timestamp", cursor.toString());
            point.put("date", dateLabel);
            point.put("count", count);
            result.add(point);
            cursor = hourEnd;
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/vehicle-categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVehicleCategories() {
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String category : List.of("CAR", "BUS", "TRUCK", "MOTORCYCLE", "BICYCLE", "SCOOTER", "MOPED", "OTHER", "UNKNOWN")) {
            long count = vehicleObservationRepository.countByCategory(category);
            if (count > 0) categoryCounts.put(category, count);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : categoryCounts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", entry.getKey());
            item.put("count", entry.getValue());
            result.add(item);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/violation-trends")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getViolationTrends(
            @RequestParam(defaultValue = "30d") String period) {
        Instant now = Instant.now();
        Instant start = parsePeriod(period, now);
        List<Map<String, Object>> result = new ArrayList<>();

        Instant cursor = start;
        while (cursor.isBefore(now)) {
            Instant dayEnd = cursor.plus(Duration.ofDays(1));
            if (dayEnd.isAfter(now)) dayEnd = now;
            long overspeeding = violationRepository.countByViolationTypeAndViolatedAtBetween("OVERSPEEDING", cursor, dayEnd);
            long total = violationRepository.countByViolatedAtBetween(cursor, dayEnd);
            long other = Math.max(0, total - overspeeding);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", cursor.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString().substring(0, 10));
            point.put("count", total);
            point.put("overspeeding", overspeeding);
            point.put("other", other);
            result.add(point);
            cursor = dayEnd;
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/overspeeding-trends")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOverspeedingTrends(
            @RequestParam(defaultValue = "30d") String period) {
        Instant now = Instant.now();
        Instant start = parsePeriod(period, now);
        List<Map<String, Object>> result = new ArrayList<>();

        Instant cursor = start;
        while (cursor.isBefore(now)) {
            Instant dayEnd = cursor.plus(Duration.ofDays(1));
            if (dayEnd.isAfter(now)) dayEnd = now;
            long count = violationRepository.countByViolationTypeAndViolatedAtBetween("OVERSPEEDING", cursor, dayEnd);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", cursor.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString());
            point.put("count", count);
            result.add(point);
            cursor = dayEnd;
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Instant parsePeriod(String period, Instant now) {
        return switch (period) {
            case "24h" -> now.minus(Duration.ofHours(24));
            case "7d" -> now.minus(Duration.ofDays(7));
            case "30d" -> now.minus(Duration.ofDays(30));
            case "90d" -> now.minus(Duration.ofDays(90));
            default -> now.minus(Duration.ofDays(7));
        };
    }
}
