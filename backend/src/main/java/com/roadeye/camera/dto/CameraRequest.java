package com.roadeye.camera.dto;

import com.roadeye.camera.entity.Camera.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraRequest {

    @NotBlank(message = "Camera code is required")
    private String cameraCode;

    @NotBlank(message = "Camera name is required")
    private String name;

    private String description;

    @NotNull(message = "Source type is required")
    private SourceType sourceType;

    private String sourceUrl;

    private String location;

    private Double latitude;

    private Double longitude;

    private String direction;

    private Integer processingFps;

    private boolean active;
}
