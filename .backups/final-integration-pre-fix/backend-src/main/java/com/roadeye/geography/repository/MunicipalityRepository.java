package com.roadeye.geography.repository;

import com.roadeye.geography.entity.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MunicipalityRepository extends JpaRepository<Municipality, UUID> {
    Optional<Municipality> findByCode(String code);
    List<Municipality> findByDistrictId(UUID districtId);
}
