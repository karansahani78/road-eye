package com.roadeye.vehicle.journey.repository;

import com.roadeye.vehicle.journey.entity.VehicleJourney;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleJourneyRepository extends JpaRepository<VehicleJourney, UUID> {
    Optional<VehicleJourney> findByVehicleIdentifierAndCompletedFalse(String vehicleIdentifier);
    List<VehicleJourney> findByFirstCameraId(String cameraId);
    List<VehicleJourney> findByLastCameraId(String cameraId);
    List<VehicleJourney> findByFirstSeenAtBetween(Instant start, Instant end);
    long countByCompleted(boolean completed);
    long countByFirstCameraIdAndFirstSeenAtBetween(String cameraId, Instant start, Instant end);
}
