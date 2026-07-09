package com.roadeye.vehicle.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "vehicle_tracks", indexes = {
    @Index(name = "idx_vt_track_id", columnList = "track_id"),
    @Index(name = "idx_vt_camera", columnList = "camera_id")
})
public class VehicleTrack extends BaseEntity {
    @Column(name = "camera_id", nullable = false)
    private String cameraId;

    @Column(name = "track_id", nullable = false)
    private Integer trackId;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "category")
    private String category;

    @Column(name = "direction")
    private String direction;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "lost")
    private boolean lost;

    @Column(name = "completed")
    private boolean completed;

    @Column(name = "trajectory", columnDefinition = "TEXT")
    private String trajectory;
}
