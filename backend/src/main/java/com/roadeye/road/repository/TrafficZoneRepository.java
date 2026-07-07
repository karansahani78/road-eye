package com.roadeye.road.repository;

import com.roadeye.road.entity.TrafficZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TrafficZoneRepository extends JpaRepository<TrafficZone, UUID> {
    Optional<TrafficZone> findByCode(String code);
}
