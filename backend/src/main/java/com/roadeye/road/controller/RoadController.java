package com.roadeye.road.controller;

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
    public ResponseEntity<ApiResponse<PagedResponse<Road>>> getAllRoads(Pageable pageable) {
        Page<Road> page = roadRepository.findAll(pageable);
        PagedResponse<Road> response = PagedResponse.<Road>builder()
                .content(page.getContent())
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
    public ResponseEntity<ApiResponse<Road>> getRoad(@PathVariable UUID id) {
        Road road = roadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road", "id", id));
        return ResponseEntity.ok(ApiResponse.success(road));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Road>> createRoad(@RequestBody Road road) {
        Road saved = roadRepository.save(road);
        return ResponseEntity.ok(ApiResponse.success(saved, "Road created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Road>> updateRoad(@PathVariable UUID id, @RequestBody Road road) {
        roadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road", "id", id));
        road.setId(id);
        Road saved = roadRepository.save(road);
        return ResponseEntity.ok(ApiResponse.success(saved, "Road updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoad(@PathVariable UUID id) {
        roadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road", "id", id));
        roadRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Road deleted successfully"));
    }
}
