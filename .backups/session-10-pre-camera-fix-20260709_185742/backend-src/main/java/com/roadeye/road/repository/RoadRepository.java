package com.roadeye.road.repository;

import com.roadeye.road.entity.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoadRepository extends JpaRepository<Road, UUID> {
    Optional<Road> findByCode(String code);
}
