package com.roadeye.incident.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class IncidentCreateRequest {
    private String cameraId;
    private String incidentType;
    private String severity;
    private Double latitude;
    private Double longitude;
    private String description;
    private Instant occurredAt;
    private Double confidence;
    private String source;
}
