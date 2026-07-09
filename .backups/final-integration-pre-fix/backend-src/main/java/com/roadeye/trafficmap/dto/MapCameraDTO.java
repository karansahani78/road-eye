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
public class MapCameraDTO {
    private UUID id;
    private String cameraCode;
    private String name;
    private Double latitude;
    private Double longitude;
    private String healthStatus;
    private String streamStatus;
    private String location;
    private Integer currentFps;
    private Integer vehiclesLast15Min;
    private Double averageSpeedKmh;
    private Boolean speedAvailable;
    private String congestionLevel;
    private Integer recentViolations;
    private Integer recentAlerts;
    private Instant lastHeartbeat;
    private Instant lastObservation;
}
