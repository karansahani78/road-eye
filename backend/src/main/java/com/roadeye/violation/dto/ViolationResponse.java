package com.roadeye.violation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViolationResponse {
    private UUID id;
    private String cameraId;
    private String cameraName;
    private Instant timestamp;
    private String violationType;
    private Double confidence;
    private String status;
    private String vehicleCategory;
    private String plateText;
    private String normalizedPlate;
    private String plateCountry;
    private Double speedKmh;
    private Double speedLimit;
    private Integer trackId;
    private String evidenceId;
    private Instant createdAt;
    private String detectorVersion;
}
