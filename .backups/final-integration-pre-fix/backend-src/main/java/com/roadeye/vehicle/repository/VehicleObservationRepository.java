package com.roadeye.vehicle.repository;

import com.roadeye.vehicle.entity.VehicleObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VehicleObservationRepository extends JpaRepository<VehicleObservation, UUID> {
    List<VehicleObservation> findByCameraIdAndObservedAtBetween(String cameraId, Instant start, Instant end);
    List<VehicleObservation> findByTrackId(Integer trackId);
    List<VehicleObservation> findByCategory(String category);

    long countByCategory(String category);

    long countByObservedAtBetween(Instant start, Instant end);

    @Query("SELECT DISTINCT v.category FROM VehicleObservation v WHERE v.category IS NOT NULL")
    List<String> findAllDistinctCategories();
}
