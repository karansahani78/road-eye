package com.roadeye.plate.repository;

import com.roadeye.plate.entity.PlateObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PlateObservationRepository extends JpaRepository<PlateObservation, UUID> {
    List<PlateObservation> findByNormalizedPlate(String normalizedPlate);
    List<PlateObservation> findByCameraIdAndObservedAtBetween(String cameraId, Instant start, Instant end);
    List<PlateObservation> findByCountry(String country);

    void deleteByObservedAtBefore(Instant cutoff);

    long countByObservedAtBefore(Instant cutoff);
}
