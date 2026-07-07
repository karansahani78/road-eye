package com.roadeye.camera.repository;

import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.entity.Camera.HealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CameraRepository extends JpaRepository<Camera, UUID> {

    Optional<Camera> findByCameraCode(String cameraCode);

    boolean existsByCameraCode(String cameraCode);

    List<Camera> findByActiveTrue();

    long countByHealthStatus(HealthStatus healthStatus);
}
