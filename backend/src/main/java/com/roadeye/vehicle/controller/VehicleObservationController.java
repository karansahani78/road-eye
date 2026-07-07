package com.roadeye.vehicle.controller;

import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import com.roadeye.vehicle.entity.VehicleObservation;
import com.roadeye.vehicle.repository.VehicleObservationRepository;
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
@RequestMapping("/api/v1/vehicle-observations")
public class VehicleObservationController {

    private final VehicleObservationRepository vehicleObservationRepository;

    public VehicleObservationController(VehicleObservationRepository vehicleObservationRepository) {
        this.vehicleObservationRepository = vehicleObservationRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<VehicleObservation>>> getVehicleObservations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "observedAt"));
        Page<VehicleObservation> vehiclePage = vehicleObservationRepository.findAll(pageable);
        PagedResponse<VehicleObservation> response = PagedResponse.<VehicleObservation>builder()
                .content(vehiclePage.getContent())
                .page(vehiclePage.getNumber())
                .size(vehiclePage.getSize())
                .totalElements(vehiclePage.getTotalElements())
                .totalPages(vehiclePage.getTotalPages())
                .first(vehiclePage.isFirst())
                .last(vehiclePage.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleObservation>> getVehicleObservation(@PathVariable UUID id) {
        return vehicleObservationRepository.findById(id)
                .map(vo -> ResponseEntity.ok(ApiResponse.success(vo)))
                .orElse(ResponseEntity.notFound().build());
    }
}
