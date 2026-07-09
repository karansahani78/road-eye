package com.roadeye.vehicle.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "vehicle_observations", indexes = {
    @Index(name = "idx_vo_camera_timestamp", columnList = "camera_id, observed_at"),
    @Index(name = "idx_vo_track_id", columnList = "track_id"),
    @Index(name = "idx_vo_category", columnList = "category")
})
public class VehicleObservation extends BaseEntity {
    @Column(name = "camera_id", nullable = false)
    private String cameraId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "track_id")
    private Integer trackId;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "original_class_name")
    private String originalClassName;

    @Column(name = "original_class_id")
    private Integer originalClassId;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "direction")
    private String direction;

    @Column(name = "bbox", columnDefinition = "TEXT")
    private String bbox;
}
