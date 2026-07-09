package com.roadeye.camera.controller;

import com.roadeye.camera.dto.GeocodingRequest;
import com.roadeye.camera.dto.GeocodingResult;
import com.roadeye.camera.dto.ReverseGeocodingRequest;
import com.roadeye.camera.service.GeocodingService;
import com.roadeye.shared.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/cameras/location")
public class CameraLocationController {

    private final GeocodingService geocodingService;

    public CameraLocationController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @PostMapping("/geocode")
    public ResponseEntity<ApiResponse<List<GeocodingResult>>> geocode(@Valid @RequestBody GeocodingRequest request) {
        int limit = request.getLimit() != null ? request.getLimit() : 10;
        List<GeocodingResult> results = geocodingService.search(request.getQuery(), limit);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/reverse-geocode")
    public ResponseEntity<ApiResponse<GeocodingResult>> reverseGeocode(@Valid @RequestBody ReverseGeocodingRequest request) {
        Optional<GeocodingResult> result = geocodingService.reverse(request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(ApiResponse.success(result.orElse(null)));
    }
}
