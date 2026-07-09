package com.roadeye.road.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "speed_limit_rules")
public class SpeedLimitRule extends BaseEntity {
    @Column(name = "camera_id")
    private String cameraId;

    @Column(name = "road_segment_id")
    private String roadSegmentId;

    @Column(name = "speed_limit_kmh", nullable = false)
    private Integer speedLimitKmh;

    @Column(name = "valid_from")
    private LocalTime validFrom;

    @Column(name = "valid_to")
    private LocalTime validTo;

    @Column(name = "vehicle_category")
    private String vehicleCategory;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
