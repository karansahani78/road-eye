package com.roadeye.speed.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "speed_measurements", indexes = {
    @Index(name = "idx_sm_track", columnList = "track_id"),
    @Index(name = "idx_sm_camera_timestamp", columnList = "camera_id, measured_at")
})
public class SpeedMeasurement extends BaseEntity {
    @Column(name = "camera_id", nullable = false)
    private String cameraId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "track_id")
    private Integer trackId;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "smoothed_speed_kmh")
    private Double smoothedSpeedKmh;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "duration_ms")
    private Double durationMs;

    @Column(name = "calibration_version")
    private String calibrationVersion;

    @Column(name = "algorithm_version")
    private String algorithmVersion;
}
