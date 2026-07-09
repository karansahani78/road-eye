package com.roadeye.vehicle.journey.repository;

import com.roadeye.vehicle.journey.entity.JourneySegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JourneySegmentRepository extends JpaRepository<JourneySegment, UUID> {
    List<JourneySegment> findByJourneyIdOrderByArrivedAtAsc(UUID journeyId);
    List<JourneySegment> findByCameraId(String cameraId);
    List<JourneySegment> findByCameraIdAndArrivedAtBetween(String cameraId, Instant start, Instant end);
}
