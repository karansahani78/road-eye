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
public class MapAlertDTO {
    private UUID id;
    private String type;
    private String severity;
    private String cameraId;
    private String cameraName;
    private Double latitude;
    private Double longitude;
    private String message;
    private boolean acknowledged;
    private Instant createdAt;
}
