package com.roadeye.vehicle.journey.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "journey_segments", indexes = {
    @Index(name = "idx_js_journey", columnList = "journey_id"),
    @Index(name = "idx_js_camera", columnList = "camera_id")
})
public class JourneySegment extends BaseEntity {

    @Column(name = "journey_id", nullable = false)
    private UUID journeyId;

    @Column(name = "camera_id", nullable = false)
    private String cameraId;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "direction", length = 50)
    private String direction;

    @Column(name = "lane", length = 50)
    private String lane;
}
