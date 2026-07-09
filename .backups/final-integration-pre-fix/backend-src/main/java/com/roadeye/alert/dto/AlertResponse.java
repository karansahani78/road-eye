package com.roadeye.alert.dto;

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
public class AlertResponse {
    private UUID id;
    private String type;
    private String severity;
    private String source;
    private String cameraId;
    private String cameraName;
    private String message;
    private boolean acknowledged;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private boolean resolved;
    private Instant resolvedAt;
    private Instant createdAt;
}
