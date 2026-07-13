package com.roadeye.speed.repository;

import com.roadeye.speed.entity.SpeedMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeedMeasurementRepository extends JpaRepository<SpeedMeasurement, UUID> {
    List<SpeedMeasurement> findByCameraIdAndMeasuredAtBetween(String cameraId, Instant start, Instant end);
    List<SpeedMeasurement> findByTrackId(Integer trackId);

    @Query("SELECT AVG(s.speedKmh) FROM SpeedMeasurement s")
    Optional<Double> getAverageSpeedKmh();

    @Query("SELECT AVG(s.speedKmh) FROM SpeedMeasurement s WHERE s.cameraId = :cameraId AND s.measuredAt >= :after")
    Optional<Double> getAverageSpeedKmhByCameraSince(@Param("cameraId") String cameraId, @Param("after") Instant after);

    @Query("SELECT s.cameraId, AVG(s.speedKmh) FROM SpeedMeasurement s WHERE s.cameraId IN :cameraIds AND s.measuredAt >= :after GROUP BY s.cameraId")
    List<Object[]> getAverageSpeedKmhByCameraIdsSince(@Param("cameraIds") List<String> cameraIds, @Param("after") Instant after);

    void deleteByMeasuredAtBefore(Instant cutoff);

    long countByMeasuredAtBefore(Instant cutoff);
}
