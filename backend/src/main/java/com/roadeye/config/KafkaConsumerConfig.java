package com.roadeye.config;

import com.roadeye.alert.entity.Alert;
import com.roadeye.alert.repository.AlertRepository;
import com.roadeye.plate.entity.PlateObservation;
import com.roadeye.plate.repository.PlateObservationRepository;
import com.roadeye.speed.entity.SpeedMeasurement;
import com.roadeye.speed.repository.SpeedMeasurementRepository;
import com.roadeye.vehicle.entity.VehicleObservation;
import com.roadeye.vehicle.entity.VehicleTrack;
import com.roadeye.vehicle.repository.VehicleObservationRepository;
import com.roadeye.vehicle.repository.VehicleTrackRepository;
import com.roadeye.violation.entity.Violation;
import com.roadeye.violation.entity.ViolationEvidence;
import com.roadeye.violation.service.ViolationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private final VehicleObservationRepository vehicleObservationRepository;
    private final VehicleTrackRepository vehicleTrackRepository;
    private final PlateObservationRepository plateObservationRepository;
    private final SpeedMeasurementRepository speedMeasurementRepository;
    private final ViolationService violationService;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public KafkaConsumerConfig(VehicleObservationRepository vehicleObservationRepository,
                               VehicleTrackRepository vehicleTrackRepository,
                               PlateObservationRepository plateObservationRepository,
                               SpeedMeasurementRepository speedMeasurementRepository,
                               ViolationService violationService,
                               AlertRepository alertRepository,
                               SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper) {
        this.vehicleObservationRepository = vehicleObservationRepository;
        this.vehicleTrackRepository = vehicleTrackRepository;
        this.plateObservationRepository = plateObservationRepository;
        this.speedMeasurementRepository = speedMeasurementRepository;
        this.violationService = violationService;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.camera-health:camera-health}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCameraHealth(@Payload String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.debug("Received camera health event: {}", message);
        } catch (Exception e) {
            log.error("Error processing camera health event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${kafka.topics.vehicle-observations:vehicle-observations}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleVehicleObservation(@Payload String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Map<String, Object> payload = extractPayload(data);

            VehicleObservation obs = new VehicleObservation();
            obs.setCameraId(getString(payload, "camera_id"));
            obs.setObservedAt(Instant.now());
            obs.setTrackId(getInteger(payload, "track_id"));
            obs.setCategory(getString(payload, "category"));
            obs.setOriginalClassName(getString(payload, "original_class_name"));
            obs.setOriginalClassId(getInteger(payload, "original_class_id"));
            obs.setConfidence(getDouble(payload, "confidence"));
            obs.setModelName(getString(payload, "model_name"));
            obs.setModelVersion(getString(payload, "model_version"));
            obs.setDirection(getString(payload, "direction"));
            obs.setBbox(toJson(payload.get("bbox")));

            vehicleObservationRepository.save(obs);
            log.debug("Saved vehicle observation for camera {}", obs.getCameraId());
        } catch (Exception e) {
            log.error("Error processing vehicle observation: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${kafka.topics.plate-recognitions:plate-recognitions}", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePlateRecognition(@Payload String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Map<String, Object> payload = extractPayload(data);

            PlateObservation plate = new PlateObservation();
            plate.setCameraId(getString(payload, "camera_id"));
            plate.setObservedAt(Instant.now());
            plate.setPlateText(getString(payload, "plate_text"));
            plate.setNormalizedPlate(getString(payload, "normalized_plate"));
            plate.setCountry(getString(payload, "country"));
            plate.setCountryConfidence(getDouble(payload, "country_confidence"));
            plate.setOcrConfidence(getDouble(payload, "ocr_confidence"));
            plate.setQualityScore(getDouble(payload, "quality_score"));

            plateObservationRepository.save(plate);
            log.debug("Saved plate observation: {}", plate.getNormalizedPlate());
        } catch (Exception e) {
            log.error("Error processing plate recognition: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${kafka.topics.speed-estimations:speed-estimations}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleSpeedEstimation(@Payload String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Map<String, Object> payload = extractPayload(data);

            SpeedMeasurement speed = new SpeedMeasurement();
            speed.setCameraId(getString(payload, "camera_id"));
            speed.setMeasuredAt(Instant.now());
            speed.setTrackId(getInteger(payload, "track_id"));
            speed.setSpeedKmh(getDouble(payload, "speed_kmh"));
            speed.setSmoothedSpeedKmh(getDouble(payload, "smoothed_speed_kmh"));
            speed.setConfidence(getDouble(payload, "confidence"));
            speed.setDistanceM(getDouble(payload, "distance_m"));
            speed.setDurationMs(getDouble(payload, "duration_ms"));
            speed.setAlgorithmVersion(getString(payload, "algorithm_version"));

            speedMeasurementRepository.save(speed);
            log.debug("Saved speed measurement: {} km/h", speed.getSpeedKmh());
        } catch (Exception e) {
            log.error("Error processing speed estimation: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${kafka.topics.violation-candidates:violation-candidates}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleViolationCandidate(@Payload String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Map<String, Object> payload = extractPayload(data);

            Violation violation = new Violation();
            violation.setCameraId(getString(payload, "camera_id"));
            violation.setViolatedAt(Instant.now());
            violation.setViolationType(getString(payload, "violation_type"));
            violation.setConfidence(getDouble(payload, "confidence"));
            violation.setStatus("DETECTED");
            violation.setVehicleCategory(getString(payload, "vehicle_category"));
            violation.setPlateText(getString(payload, "plate_text"));
            violation.setNormalizedPlate(getString(payload, "normalized_plate"));
            violation.setPlateCountry(getString(payload, "plate_country"));
            violation.setSpeedKmh(getDouble(payload, "speed_kmh"));
            violation.setSpeedLimit(getDouble(payload, "speed_limit"));
            violation.setTrackId(getInteger(payload, "track_id"));

            violation = violationService.createViolation(violation);

            sendRealTimeAlert(violation);
            log.info("Saved violation: {} for camera {}", violation.getViolationType(), violation.getCameraId());
        } catch (Exception e) {
            log.error("Error processing violation candidate: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${kafka.topics.evidence-created:evidence-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEvidenceCreated(@Payload String message) {
        try {
            log.debug("Evidence created event received: {}", message);
        } catch (Exception e) {
            log.error("Error processing evidence event: {}", e.getMessage());
        }
    }

    private void sendRealTimeAlert(Violation violation) {
        try {
            Alert alert = new Alert();
            alert.setType("VIOLATION_DETECTED");
            alert.setSeverity(violation.getConfidence() != null && violation.getConfidence() > 0.8 ? "HIGH" : "MEDIUM");
            alert.setSource("AI_ENGINE");
            alert.setCameraId(violation.getCameraId());
            alert.setMessage(String.format("%s detected with %.0f%% confidence",
                    violation.getViolationType(),
                    (violation.getConfidence() != null ? violation.getConfidence() : 0) * 100));
            alert.setAcknowledged(false);
            alert.setResolved(false);

            alert = alertRepository.save(alert);

            messagingTemplate.convertAndSend("/topic/alerts", alert);
        } catch (Exception e) {
            log.error("Error sending real-time alert: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> event) {
        if (event.containsKey("payload") && event.get("payload") instanceof Map) {
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            payload.putIfAbsent("camera_id", getString(event, "camera_id"));
            return payload;
        }
        return event;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
