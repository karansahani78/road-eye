package com.roadeye.camera.service;

import com.roadeye.camera.entity.Camera;
import com.roadeye.camera.entity.CameraHealthEvent;
import com.roadeye.camera.repository.CameraHealthEventRepository;
import com.roadeye.camera.repository.CameraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CameraEventService {

    private static final Logger log = LoggerFactory.getLogger(CameraEventService.class);

    private final CameraRepository cameraRepository;
    private final CameraHealthEventRepository healthEventRepository;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public CameraEventService(CameraRepository cameraRepository,
                              CameraHealthEventRepository healthEventRepository,
                              RestTemplate restTemplate) {
        this.cameraRepository = cameraRepository;
        this.healthEventRepository = healthEventRepository;
        this.restTemplate = restTemplate;
    }

    public void startCameraStream(Camera camera) {
        try {
            String url = aiServiceUrl + "/api/v1/streams/start";
            restTemplate.postForEntity(url, Map.of(
                    "camera_id", camera.getId().toString(),
                    "source_url", camera.getSourceUrlEncrypted(),
                    "fps", camera.getProcessingFps()
            ), String.class);
            log.info("Requested AI stream start for camera {}", camera.getId());
        } catch (Exception e) {
            log.error("Failed to start AI stream for camera {}: {}", camera.getId(), e.getMessage());
        }
    }

    public void stopCameraStream(Camera camera) {
        try {
            String url = aiServiceUrl + "/api/v1/streams/stop";
            restTemplate.postForEntity(url, Map.of("camera_id", camera.getId().toString()), String.class);
            log.info("Requested AI stream stop for camera {}", camera.getId());
        } catch (Exception e) {
            log.error("Failed to stop AI stream for camera {}: {}", camera.getId(), e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkCameraHealth() {
        List<Camera> activeCameras = cameraRepository.findByActiveTrue();
        for (Camera camera : activeCameras) {
            CameraHealthEvent event = new CameraHealthEvent();
            event.setCamera(camera);
            event.setStatus(camera.getHealthStatus().name());
            event.setRecordedAt(Instant.now());
            healthEventRepository.save(event);
        }
    }
}
