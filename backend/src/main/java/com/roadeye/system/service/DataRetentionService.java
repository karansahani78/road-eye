package com.roadeye.system.service;

import com.roadeye.audit.repository.AuditLogRepository;
import com.roadeye.camera.repository.CameraHealthEventRepository;
import com.roadeye.plate.repository.PlateObservationRepository;
import com.roadeye.speed.repository.SpeedMeasurementRepository;
import com.roadeye.vehicle.repository.VehicleObservationRepository;
import com.roadeye.vehicle.repository.VehicleTrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final VehicleObservationRepository vehicleObservationRepository;
    private final VehicleTrackRepository vehicleTrackRepository;
    private final SpeedMeasurementRepository speedMeasurementRepository;
    private final CameraHealthEventRepository cameraHealthEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final PlateObservationRepository plateObservationRepository;

    public DataRetentionService(VehicleObservationRepository vehicleObservationRepository,
                                 VehicleTrackRepository vehicleTrackRepository,
                                 SpeedMeasurementRepository speedMeasurementRepository,
                                 CameraHealthEventRepository cameraHealthEventRepository,
                                 AuditLogRepository auditLogRepository,
                                 PlateObservationRepository plateObservationRepository) {
        this.vehicleObservationRepository = vehicleObservationRepository;
        this.vehicleTrackRepository = vehicleTrackRepository;
        this.speedMeasurementRepository = speedMeasurementRepository;
        this.cameraHealthEventRepository = cameraHealthEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.plateObservationRepository = plateObservationRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runRetentionCleanup() {
        log.info("Starting data retention cleanup...");
        long totalDeleted = 0;
        Map<String, Long> results = new LinkedHashMap<>();

        // Vehicle observations: keep 90 days
        Instant observationsCutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        long observationsCount = vehicleObservationRepository.countByObservedAtBefore(observationsCutoff);
        if (observationsCount > 0) {
            vehicleObservationRepository.deleteByObservedAtBefore(observationsCutoff);
            results.put("vehicle_observations", observationsCount);
            totalDeleted += observationsCount;
        }

        // Vehicle tracks: keep 30 days
        Instant tracksCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        long tracksCount = vehicleTrackRepository.countByLastSeenBefore(tracksCutoff);
        if (tracksCount > 0) {
            vehicleTrackRepository.deleteByLastSeenBefore(tracksCutoff);
            results.put("vehicle_tracks", tracksCount);
            totalDeleted += tracksCount;
        }

        // Speed measurements: keep 90 days
        Instant speedsCutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        long speedsCount = speedMeasurementRepository.countByMeasuredAtBefore(speedsCutoff);
        if (speedsCount > 0) {
            speedMeasurementRepository.deleteByMeasuredAtBefore(speedsCutoff);
            results.put("speed_measurements", speedsCount);
            totalDeleted += speedsCount;
        }

        // Camera health events: keep 30 days
        Instant healthCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        long healthCount = cameraHealthEventRepository.countByRecordedAtBefore(healthCutoff);
        if (healthCount > 0) {
            cameraHealthEventRepository.deleteByRecordedAtBefore(healthCutoff);
            results.put("camera_health_events", healthCount);
            totalDeleted += healthCount;
        }

        // Audit logs: keep 180 days
        Instant auditCutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        long auditCount = auditLogRepository.countByCreatedAtBefore(auditCutoff);
        if (auditCount > 0) {
            auditLogRepository.deleteByCreatedAtBefore(auditCutoff);
            results.put("audit_logs", auditCount);
            totalDeleted += auditCount;
        }

        // Plate observations: keep 90 days
        Instant platesCutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        long platesCount = plateObservationRepository.countByObservedAtBefore(platesCutoff);
        if (platesCount > 0) {
            plateObservationRepository.deleteByObservedAtBefore(platesCutoff);
            results.put("plate_observations", platesCount);
            totalDeleted += platesCount;
        }

        if (totalDeleted > 0) {
            log.info("Data retention cleanup completed. Deleted {} rows: {}", totalDeleted, results);
        } else {
            log.debug("Data retention cleanup completed. No stale data found.");
        }
    }

    public Map<String, Object> getRetentionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        Instant now = Instant.now();
        status.put("vehicle_observations_90d", vehicleObservationRepository.countByObservedAtBefore(now.minus(90, ChronoUnit.DAYS)));
        status.put("vehicle_tracks_30d", vehicleTrackRepository.countByLastSeenBefore(now.minus(30, ChronoUnit.DAYS)));
        status.put("speed_measurements_90d", speedMeasurementRepository.countByMeasuredAtBefore(now.minus(90, ChronoUnit.DAYS)));
        status.put("camera_health_events_30d", cameraHealthEventRepository.countByRecordedAtBefore(now.minus(30, ChronoUnit.DAYS)));
        status.put("audit_logs_180d", auditLogRepository.countByCreatedAtBefore(now.minus(180, ChronoUnit.DAYS)));
        status.put("plate_observations_90d", plateObservationRepository.countByObservedAtBefore(now.minus(90, ChronoUnit.DAYS)));

        return status;
    }
}
