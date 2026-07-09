package com.roadeye.road.repository;

import com.roadeye.road.entity.RoadSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RoadSegmentRepository extends JpaRepository<RoadSegment, UUID> {
    List<RoadSegment> findByRoadId(UUID roadId);
}
