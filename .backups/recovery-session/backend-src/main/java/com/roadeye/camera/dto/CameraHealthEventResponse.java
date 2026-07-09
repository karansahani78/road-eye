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
public class CameraHealthEventResponse {
    private UUID id;
    private UUID cameraId;
    private String cameraName;
    private String status;
    private String message;
    private String errorType;
    private Long processedFrames;
    private Long droppedFrames;
    private Double inferenceLatencyMs;
    private Instant recordedAt;
    private Instant createdAt;
}
