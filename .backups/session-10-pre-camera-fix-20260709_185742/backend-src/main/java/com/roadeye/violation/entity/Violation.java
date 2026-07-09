package com.roadeye.violation.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "violations", indexes = {
    @Index(name = "idx_v_camera", columnList = "camera_id"),
    @Index(name = "idx_v_type", columnList = "violation_type"),
    @Index(name = "idx_v_status", columnList = "status"),
    @Index(name = "idx_v_timestamp", columnList = "violated_at"),
    @Index(name = "idx_v_camera_timestamp", columnList = "camera_id, violated_at")
})
public class Violation extends BaseEntity {
    @Column(name = "camera_id", nullable = false)
    private String cameraId;

    @Column(name = "violated_at", nullable = false)
    private Instant violatedAt;

    @Column(name = "violation_type", nullable = false, length = 50)
    private String violationType;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "vehicle_category", length = 100)
    private String vehicleCategory;

    @Column(name = "plate_text")
    private String plateText;

    @Column(name = "normalized_plate")
    private String normalizedPlate;

    @Column(name = "plate_country")
    private String plateCountry;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "speed_limit")
    private Double speedLimit;

    @Column(name = "track_id")
    private Integer trackId;

    @Column(name = "detector_version")
    private String detectorVersion;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
