package com.roadeye.vehicle.repository;

import com.roadeye.vehicle.entity.VehicleTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleTrackRepository extends JpaRepository<VehicleTrack, UUID> {
    Optional<VehicleTrack> findByCameraIdAndTrackId(String cameraId, Integer trackId);
    List<VehicleTrack> findByCameraId(String cameraId);
    List<VehicleTrack> findByCompleted(boolean completed);

    long countByCameraIdAndLastSeenAfter(String cameraId, Instant after);

    @Query("SELECT COUNT(DISTINCT vt.trackId) FROM VehicleTrack vt WHERE vt.lastSeen >= :after")
    long countDistinctActiveTracksSince(@Param("after") Instant after);

    @Query("SELECT vt.cameraId, COUNT(vt) FROM VehicleTrack vt WHERE vt.cameraId IN :cameraIds AND vt.lastSeen >= :after GROUP BY vt.cameraId")
    List<Object[]> countByCameraIdsAndLastSeenAfter(@Param("cameraIds") List<String> cameraIds, @Param("after") Instant after);

    void deleteByLastSeenBefore(Instant cutoff);

    long countByLastSeenBefore(Instant cutoff);
}
