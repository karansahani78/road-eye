package com.roadeye.camera.repository;

import com.roadeye.camera.entity.CameraCalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CameraCalibrationRepository extends JpaRepository<CameraCalibration, UUID> {

    Optional<CameraCalibration> findByCameraId(UUID cameraId);

    Optional<CameraCalibration> findByCameraIdAndActiveTrue(UUID cameraId);
}
