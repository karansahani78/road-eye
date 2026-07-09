package com.roadeye.road.repository;

import com.roadeye.road.entity.SpeedLimitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpeedLimitRuleRepository extends JpaRepository<SpeedLimitRule, UUID> {
    List<SpeedLimitRule> findByCameraId(String cameraId);
    List<SpeedLimitRule> findByRoadSegmentId(String roadSegmentId);
}
