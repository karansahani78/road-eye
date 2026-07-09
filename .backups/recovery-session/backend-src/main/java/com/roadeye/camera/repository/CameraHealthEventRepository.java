package com.roadeye.camera.repository;

import com.roadeye.camera.entity.CameraHealthEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface CameraHealthEventRepository extends JpaRepository<CameraHealthEvent, UUID> {

    Page<CameraHealthEvent> findByCameraIdOrderByRecordedAtDesc(UUID cameraId, Pageable pageable);

    Page<CameraHealthEvent> findByCameraIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID cameraId, Instant start, Instant end, Pageable pageable);
}
