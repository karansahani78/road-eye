package com.roadeye.road.controller;

import com.roadeye.road.dto.RoadResponse;
import com.roadeye.road.entity.Intersection;
import com.roadeye.road.entity.Road;
import com.roadeye.road.entity.RoadSegment;
import com.roadeye.road.entity.SpeedLimitRule;
import com.roadeye.road.entity.TrafficZone;
import com.roadeye.road.repository.IntersectionRepository;
import com.roadeye.road.repository.RoadRepository;
import com.roadeye.road.repository.RoadSegmentRepository;
import com.roadeye.road.repository.SpeedLimitRuleRepository;
import com.roadeye.road.repository.TrafficZoneRepository;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import com.roadeye.shared.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roads")
public class RoadController {

    private final RoadRepository roadRepository;
    private final RoadSegmentRepository roadSegmentRepository;
    private final TrafficZoneRepository trafficZoneRepository;
    private final IntersectionRepository intersectionRepository;
    private final SpeedLimitRuleRepository speedLimitRuleRepository;

    public RoadController(RoadRepository roadRepository,
                          RoadSegmentRepository roadSegmentRepository,
                          TrafficZoneRepository trafficZoneRepository,
                          IntersectionRepository intersectionRepository,
                          SpeedLimitRuleRepository speedLimitRuleRepository) {
        this.roadRepository = roadRepository;
        this.roadSegmentRepository = roadSegmentRepository;
        this.trafficZoneRepository = trafficZoneRepository;
        this.intersectionRepository = intersectionRepository;
        this.speedLimitRuleRepository = speedLimitRuleRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<RoadResponse>>> getAllRoads(Pageable pageable) {
        Page<Road> page = roadRepository.findAll(pageable);
        PagedResponse<RoadResponse> response = PagedResponse.<RoadResponse>builder()
                .content(page.getContent().stream().map(this::mapToResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/segments")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getSegments(Pageable pageable) {
        Page<RoadSegment> page = roadSegmentRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(mapPage(page, this::mapSegment)));
    }

    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getZones(Pageable pageable) {
        Page<TrafficZone> page = trafficZoneRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(mapPage(page, this::mapZone)));
    }

    @GetMapping("/intersections")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getIntersections(Pageable pageable) {
        Page<Intersection> page = intersectionRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(mapPage(page, this::mapIntersection)));
    }

    @GetMapping("/speed-limits")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getSpeedLimits(Pageable pageable) {
        Page<SpeedLimitRule> page = speedLimitRuleRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(mapPage(page, this::mapSpeedLimit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoadResponse>> getRoad(@PathVariable UUID id) {
        Road road = roadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road", "id", id));
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(road)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoadResponse>> createRoad(@RequestBody Road road) {
        Road saved = roadRepository.save(road);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Road created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoadResponse>> updateRoad(@PathVariable UUID id, @RequestBody Road road) {
        roadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road", "id", id));
        road.setId(id);
        Road saved = roadRepository.save(road);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Road updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoad(@PathVariable UUID id) {
        roadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road", "id", id));
        roadRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Road deleted successfully"));
    }

    private <E> PagedResponse<Map<String, Object>> mapPage(Page<E> page, java.util.function.Function<E, Map<String, Object>> mapper) {
        return PagedResponse.<Map<String, Object>>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private Map<String, Object> mapSegment(RoadSegment segment) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", segment.getId());
        map.put("roadId", segment.getRoad() != null ? segment.getRoad().getId() : null);
        map.put("roadName", segment.getRoad() != null ? segment.getRoad().getName() : null);
        map.put("name", segment.getName());
        map.put("startPoint", segment.getStartPoint());
        map.put("endPoint", segment.getEndPoint());
        map.put("speedLimit", segment.getSpeedLimit());
        map.put("lengthMeters", segment.getLengthMeters());
        return map;
    }

    private Map<String, Object> mapZone(TrafficZone zone) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", zone.getId());
        map.put("name", zone.getName());
        map.put("code", zone.getCode());
        map.put("zoneType", zone.getZoneType());
        map.put("speedLimit", zone.getSpeedLimit());
        map.put("restricted", zone.isRestricted());
        return map;
    }

    private Map<String, Object> mapIntersection(Intersection intersection) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", intersection.getId());
        map.put("name", intersection.getName());
        map.put("code", intersection.getCode());
        map.put("latitude", intersection.getLatitude());
        map.put("longitude", intersection.getLongitude());
        map.put("hasTrafficSignal", intersection.isHasTrafficSignal());
        return map;
    }

    private Map<String, Object> mapSpeedLimit(SpeedLimitRule rule) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("cameraId", rule.getCameraId());
        map.put("roadSegmentId", rule.getRoadSegmentId());
        map.put("speedLimitKmh", rule.getSpeedLimitKmh());
        map.put("vehicleCategory", rule.getVehicleCategory());
        map.put("validFrom", rule.getValidFrom());
        map.put("validTo", rule.getValidTo());
        map.put("active", rule.isActive());
        return map;
    }

    private RoadResponse mapToResponse(Road road) {
        return RoadResponse.builder()
                .id(road.getId())
                .name(road.getName())
                .code(road.getCode())
                .roadType(road.getRoadType())
                .province(road.getProvince())
                .district(road.getDistrict())
                .length(road.getLength())
                .speedLimit(road.getDefaultSpeedLimit())
                .build();
    }
}
