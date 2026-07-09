package com.roadeye.camera.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "camera_health_events")
public class CameraHealthEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "processed_frames")
    private Long processedFrames = 0L;

    @Column(name = "dropped_frames")
    private Long droppedFrames = 0L;

    @Column(name = "inference_latency_ms")
    private Double inferenceLatencyMs;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
