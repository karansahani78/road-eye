package com.roadeye.road.repository;

import com.roadeye.road.entity.Intersection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IntersectionRepository extends JpaRepository<Intersection, UUID> {
    Optional<Intersection> findByCode(String code);
}
