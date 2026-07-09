package com.roadeye.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraCalibrationRequest {

    private String calibrationData;
    private String referencePoint1;
    private String referencePoint2;
    private Double knownDistance;
    private String algorithmVersion;
}
