package com.roadeye.incident.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_camera", columnList = "camera_id"),
    @Index(name = "idx_incident_type", columnList = "incident_type"),
    @Index(name = "idx_incident_status", columnList = "status"),
    @Index(name = "idx_incident_severity", columnList = "severity"),
    @Index(name = "idx_incident_detected", columnList = "detected_at"),
    @Index(name = "idx_incident_camera_detected", columnList = "camera_id, detected_at")
})
public class Incident extends BaseEntity {

    @Column(name = "camera_id")
    private String cameraId;

    @Column(name = "incident_type", nullable = false, length = 100)
    private String incidentType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "status", nullable = false, length = 50)
    private String status = "DETECTED";

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "source", length = 100)
    private String source = "AI_ENGINE";

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
