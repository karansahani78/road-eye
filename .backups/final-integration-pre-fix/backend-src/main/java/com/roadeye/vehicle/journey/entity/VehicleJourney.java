package com.roadeye.vehicle.journey.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "vehicle_journeys", indexes = {
    @Index(name = "idx_vj_identifier", columnList = "vehicle_identifier"),
    @Index(name = "idx_vj_identifier_type", columnList = "identifier_type"),
    @Index(name = "idx_vj_first_camera", columnList = "first_camera_id"),
    @Index(name = "idx_vj_completed", columnList = "completed")
})
public class VehicleJourney extends BaseEntity {

    @Column(name = "vehicle_identifier")
    private String vehicleIdentifier;

    @Column(name = "identifier_type", nullable = false, length = 50)
    private String identifierType = "PLATE";

    @Column(name = "first_camera_id")
    private String firstCameraId;

    @Column(name = "last_camera_id")
    private String lastCameraId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "camera_count")
    private Integer cameraCount = 1;

    @Column(name = "observation_count")
    private Integer observationCount = 1;

    @Column(name = "total_distance_km")
    private Double totalDistanceKm;

    @Column(name = "average_speed_kmh")
    private Double averageSpeedKmh;

    @Column(name = "route_geometry", columnDefinition = "TEXT")
    private String routeGeometry;

    @Column(name = "completed")
    private boolean completed;
}
