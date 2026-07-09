package com.roadeye.camera.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "camera_calibrations")
public class CameraCalibration extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", unique = true, nullable = false)
    private Camera camera;

    @Column(name = "calibration_version", length = 50)
    private String calibrationVersion;

    @Column(name = "calibration_data", columnDefinition = "TEXT")
    private String calibrationData;

    @Column(name = "reference_point1", length = 255)
    private String referencePoint1;

    @Column(name = "reference_point2", length = 255)
    private String referencePoint2;

    @Column(name = "known_distance")
    private Double knownDistance;

    @Column(name = "perspective_transform", columnDefinition = "TEXT")
    private String perspectiveTransform;

    @Column(name = "algorithm_version", length = 100)
    private String algorithmVersion;

    @Column(name = "calibrated_by", length = 100)
    private String calibratedBy;

    @Column(name = "calibrated_at")
    private Instant calibratedAt;

    @Column(name = "active")
    private boolean active = true;
}
