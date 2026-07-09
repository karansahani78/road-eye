package com.roadeye.camera.dto;

import com.roadeye.camera.entity.Camera.HealthStatus;
import com.roadeye.camera.entity.Camera.LocationSource;
import com.roadeye.camera.entity.Camera.SourceType;
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
public class CameraResponse {

    private UUID id;
    private String cameraCode;
    private String name;
    private String description;
    private SourceType sourceType;
    private String location;
    private Double latitude;
    private Double longitude;
    private String direction;
    private Integer processingFps;
    private String resolution;
    private boolean active;
    private HealthStatus healthStatus;
    private Instant lastHeartbeat;
    private Instant lastSuccessfulFrame;
    private Instant createdAt;
    private Instant updatedAt;

    private LocationSource locationSource;

    private Double locationAccuracyMeters;

    private boolean sourceUrlConfigured;
}
