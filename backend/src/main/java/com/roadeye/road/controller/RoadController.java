package com.roadeye.road.controller;

import com.roadeye.road.dto.RoadResponse;
import com.roadeye.road.entity.Road;
import com.roadeye.road.repository.RoadRepository;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import com.roadeye.shared.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roads")
public class RoadController {

    private final RoadRepository roadRepository;

    public RoadController(RoadRepository roadRepository) {
        this.roadRepository = roadRepository;
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
