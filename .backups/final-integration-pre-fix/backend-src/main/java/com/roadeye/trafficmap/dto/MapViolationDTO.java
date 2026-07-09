package com.roadeye.trafficmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapViolationDTO {
    private UUID id;
    private String violationType;
    private String cameraId;
    private String cameraName;
    private Double latitude;
    private Double longitude;
    private String status;
    private Double confidence;
    private String vehicleCategory;
    private String plateText;
    private Double speedKmh;
    private Instant timestamp;
}
