package com.roadeye.speed.controller;

import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import com.roadeye.speed.entity.SpeedMeasurement;
import com.roadeye.speed.repository.SpeedMeasurementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/speed-measurements")
public class SpeedMeasurementController {

    private final SpeedMeasurementRepository speedMeasurementRepository;

    public SpeedMeasurementController(SpeedMeasurementRepository speedMeasurementRepository) {
        this.speedMeasurementRepository = speedMeasurementRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<SpeedMeasurement>>> getSpeedMeasurements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "measuredAt"));
        Page<SpeedMeasurement> speedPage = speedMeasurementRepository.findAll(pageable);
        PagedResponse<SpeedMeasurement> response = PagedResponse.<SpeedMeasurement>builder()
                .content(speedPage.getContent())
                .page(speedPage.getNumber())
                .size(speedPage.getSize())
                .totalElements(speedPage.getTotalElements())
                .totalPages(speedPage.getTotalPages())
                .first(speedPage.isFirst())
                .last(speedPage.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpeedMeasurement>> getSpeedMeasurement(@PathVariable UUID id) {
        return speedMeasurementRepository.findById(id)
                .map(sm -> ResponseEntity.ok(ApiResponse.success(sm)))
                .orElse(ResponseEntity.notFound().build());
    }
}
