package com.roadeye.speed.repository;

import com.roadeye.speed.entity.SpeedMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpeedMeasurementRepository extends JpaRepository<SpeedMeasurement, UUID> {
    List<SpeedMeasurement> findByCameraIdAndMeasuredAtBetween(String cameraId, Instant start, Instant end);
    List<SpeedMeasurement> findByTrackId(Integer trackId);
}
