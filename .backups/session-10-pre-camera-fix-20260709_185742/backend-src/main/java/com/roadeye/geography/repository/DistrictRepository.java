package com.roadeye.geography.repository;

import com.roadeye.geography.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DistrictRepository extends JpaRepository<District, UUID> {
    Optional<District> findByCode(String code);
    List<District> findByProvinceId(UUID provinceId);
}
