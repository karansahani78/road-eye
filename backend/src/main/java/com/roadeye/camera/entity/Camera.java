package com.roadeye.camera.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "cameras")
public class Camera extends BaseEntity {

    public enum SourceType {
        RTSP, VIDEO_FILE, CCTV, IP_CAMERA
    }

    public enum HealthStatus {
        ONLINE, OFFLINE, DEGRADED, UNKNOWN
    }

    @Column(name = "camera_code", unique = true, nullable = false, length = 100)
    private String cameraCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(name = "source_url_encrypted", columnDefinition = "TEXT")
    private String sourceUrlEncrypted;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "direction", length = 50)
    private String direction;

    @Column(name = "processing_fps")
    private Integer processingFps = 15;

    @Column(name = "resolution", length = 50)
    private String resolution;

    @Column(name = "active")
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 50)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "last_successful_frame")
    private Instant lastSuccessfulFrame;
}
