package com.roadeye.incident.dto;

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
public class IncidentResponse {
    private UUID id;
    private String cameraId;
    private String cameraName;
    private String incidentType;
    private String severity;
    private String status;
    private Double latitude;
    private Double longitude;
    private String description;
    private Instant detectedAt;
    private Instant occurredAt;
    private Instant resolvedAt;
    private String assignedTo;
    private Instant assignedAt;
    private String resolutionNotes;
    private String source;
    private Double confidence;
    private Instant createdAt;
    private Instant updatedAt;
}
