package com.roadeye.vehicle.repository;

import com.roadeye.vehicle.entity.VehicleTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleTrackRepository extends JpaRepository<VehicleTrack, UUID> {
    Optional<VehicleTrack> findByCameraIdAndTrackId(String cameraId, Integer trackId);
    List<VehicleTrack> findByCameraId(String cameraId);
    List<VehicleTrack> findByCompleted(boolean completed);
}
