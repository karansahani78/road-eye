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
import java.time.temporal.ChronoUnit;
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
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);

        long totalCameras = cameraRepository.count();
        long onlineCameras = cameraRepository.countByHealthStatus(Camera.HealthStatus.ONLINE);
        long offlineCameras = cameraRepository.countByHealthStatus(Camera.HealthStatus.OFFLINE);
        long activeStreams = cameraRepository.countByActiveTrue();
        long vehiclesDetected = vehicleObservationRepository.count();
        long trafficVolumeToday = vehicleObservationRepository.countByObservedAtBetween(todayStart, now);

        // Single grouped query instead of 5 separate countByStatus calls
        Map<String, Long> statusCounts = new HashMap<>();
        List<Object[]> grouped = violationRepository.countGroupByStatus();
        for (Object[] row : grouped) {
            statusCounts.put((String) row[0], (Long) row[1]);
        }

        long activeViolations = statusCounts.getOrDefault("PENDING_REVIEW", 0L)
                + statusCounts.getOrDefault("UNDER_REVIEW", 0L)
                + statusCounts.getOrDefault("NEEDS_ADDITIONAL_REVIEW", 0L)
                + statusCounts.getOrDefault("CONFIRMED", 0L)
                + statusCounts.getOrDefault("FORWARDED_TO_AUTHORITY", 0L);
        long pendingReviews = statusCounts.getOrDefault("PENDING_REVIEW", 0L);
        long confirmedViolations = statusCounts.getOrDefault("CONFIRMED", 0L);
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
        Instant now = Instant.now();
        Instant start = parsePeriod(period, now);

        // Single batch query instead of N hourly queries
        List<Object[]> hourlyCounts = vehicleObservationRepository.countByHourBetween(start, now);
        Map<String, Long> countByHour = new LinkedHashMap<>();
        for (Object[] row : hourlyCounts) {
            // row[0] = truncated hour (java.sql.Timestamp from date_trunc), row[1] = count
            String hourKey = row[0].toString().substring(0, 13); // "2026-07-09T14"
            countByHour.merge(hourKey, (Long) row[1], Long::sum);
        }

        // Fill in zeros for missing hours
        List<Map<String, Object>> result = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(now)) {
            Instant hourEnd = cursor.plus(Duration.ofHours(1));
            String hourKey = cursor.toString().substring(0, 13);
            String dateLabel = cursor.truncatedTo(ChronoUnit.DAYS).toString().substring(0, 10);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", cursor.toString());
            point.put("date", dateLabel);
            point.put("count", countByHour.getOrDefault(hourKey, 0L));
            result.add(point);
            cursor = hourEnd;
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/vehicle-categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVehicleCategories() {
        // Single batch query instead of N category queries
        List<Object[]> grouped = vehicleObservationRepository.countGroupByCategory();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (Object[] row : grouped) {
            String category = (String) row[0];
            Long count = (Long) row[1];
            if (count > 0) categoryCounts.put(category, count);
        }
        if (categoryCounts.isEmpty()) {
            for (String category : List.of("CAR", "BUS", "TRUCK", "MOTORCYCLE", "BICYCLE", "OTHER", "UNKNOWN")) {
                categoryCounts.put(category, 0L);
            }
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

        // Two batch queries instead of 2*N daily queries
        List<Object[]> dailyTotal = violationRepository.countByDayBetween(start, now);
        List<Object[]> dailyOverspeeding = violationRepository.countByTypeAndDayBetween("OVERSPEEDING", start, now);

        Map<String, Long> totalCountByDay = new LinkedHashMap<>();
        for (Object[] row : dailyTotal) {
            String dayKey = row[0].toString().substring(0, 10);
            totalCountByDay.put(dayKey, (Long) row[1]);
        }
        Map<String, Long> overCountByDay = new LinkedHashMap<>();
        for (Object[] row : dailyOverspeeding) {
            String dayKey = row[0].toString().substring(0, 10);
            overCountByDay.put(dayKey, (Long) row[1]);
        }

        // Fill in zeros for missing days
        List<Map<String, Object>> result = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(now)) {
            Instant dayEnd = cursor.plus(Duration.ofDays(1));
            String dayKey = cursor.truncatedTo(ChronoUnit.DAYS).toString().substring(0, 10);
            long total = totalCountByDay.getOrDefault(dayKey, 0L);
            long overspeeding = overCountByDay.getOrDefault(dayKey, 0L);
            long other = Math.max(0, total - overspeeding);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", dayKey);
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

        // Single batch query instead of N daily queries
        List<Object[]> dailyCounts = violationRepository.countByTypeAndDayBetween("OVERSPEEDING", start, now);
        Map<String, Long> countByDay = new LinkedHashMap<>();
        for (Object[] row : dailyCounts) {
            String dayKey = row[0].toString().substring(0, 10);
            countByDay.put(dayKey, (Long) row[1]);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(now)) {
            Instant dayEnd = cursor.plus(Duration.ofDays(1));
            String dayKey = cursor.truncatedTo(ChronoUnit.DAYS).toString().substring(0, 10);
            long count = countByDay.getOrDefault(dayKey, 0L);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", dayKey);
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
