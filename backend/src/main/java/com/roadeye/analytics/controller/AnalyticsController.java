package com.roadeye.analytics.controller;

import com.roadeye.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCameras", 0);
        stats.put("onlineCameras", 0);
        stats.put("offlineCameras", 0);
        stats.put("activeStreams", 0);
        stats.put("vehiclesDetected", 0);
        stats.put("trafficVolume", 0);
        stats.put("averageSpeed", 0.0);
        stats.put("activeViolations", 0);
        stats.put("pendingReviews", 0);
        stats.put("confirmedViolations", 0);
        stats.put("activeCriticalAlerts", 0);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/traffic-volume")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTrafficVolume(
            @RequestParam(defaultValue = "7d") String period) {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
    }

    @GetMapping("/vehicle-categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVehicleCategories() {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
    }

    @GetMapping("/violation-trends")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getViolationTrends(
            @RequestParam(defaultValue = "30d") String period) {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
    }

    @GetMapping("/overspeeding-trends")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOverspeedingTrends(
            @RequestParam(defaultValue = "30d") String period) {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
    }
}
