package com.roadeye.trafficmap.controller;

import com.roadeye.shared.ApiResponse;
import com.roadeye.trafficmap.dto.TrafficMapResponse;
import com.roadeye.trafficmap.service.TrafficMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traffic-map")
public class TrafficMapController {

    private final TrafficMapService trafficMapService;

    public TrafficMapController(TrafficMapService trafficMapService) {
        this.trafficMapService = trafficMapService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TrafficMapResponse>> getTrafficMap(
            @RequestParam(defaultValue = "15") int minutes) {
        TrafficMapResponse response = trafficMapService.getTrafficMap(minutes);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
