package com.roadeye.camera.dto;

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
public class CameraCalibrationResponse {

    private UUID id;
    private UUID cameraId;
    private String calibrationVersion;
    private String calibrationData;
    private String referencePoint1;
    private String referencePoint2;
    private Double knownDistance;
    private String perspectiveTransform;
    private String algorithmVersion;
    private String calibratedBy;
    private Instant calibratedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
