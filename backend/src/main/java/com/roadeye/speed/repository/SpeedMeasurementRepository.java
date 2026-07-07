package com.roadeye.speed.repository;

import com.roadeye.speed.entity.SpeedMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeedMeasurementRepository extends JpaRepository<SpeedMeasurement, UUID> {
    List<SpeedMeasurement> findByCameraIdAndMeasuredAtBetween(String cameraId, Instant start, Instant end);
    List<SpeedMeasurement> findByTrackId(Integer trackId);

    @Query("SELECT AVG(s.speedKmh) FROM SpeedMeasurement s")
    Optional<Double> getAverageSpeedKmh();
}
