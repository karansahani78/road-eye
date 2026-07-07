package com.roadeye.alert.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_type", columnList = "type"),
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_camera", columnList = "camera_id"),
    @Index(name = "idx_alert_created", columnList = "created_at"),
    @Index(name = "idx_alert_acknowledged", columnList = "acknowledged")
})
public class Alert extends BaseEntity {
    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "camera_id")
    private String cameraId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "acknowledged")
    private boolean acknowledged;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved")
    private boolean resolved;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
