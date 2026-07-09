package com.roadeye.plate.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "plate_observations", indexes = {
    @Index(name = "idx_po_plate", columnList = "normalized_plate"),
    @Index(name = "idx_po_camera_timestamp", columnList = "camera_id, observed_at"),
    @Index(name = "idx_po_country", columnList = "country")
})
public class PlateObservation extends BaseEntity {
    @Column(name = "camera_id", nullable = false)
    private String cameraId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "plate_text")
    private String plateText;

    @Column(name = "normalized_plate")
    private String normalizedPlate;

    @Column(name = "country")
    private String country;

    @Column(name = "country_confidence")
    private Double countryConfidence;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "track_id")
    private Integer trackId;

    @Column(name = "bbox", columnDefinition = "TEXT")
    private String bbox;

    @Column(name = "alternatives", columnDefinition = "TEXT")
    private String alternatives;

    @Column(name = "detector_version")
    private String detectorVersion;

    @Column(name = "ocr_model_version")
    private String ocrModelVersion;
}
