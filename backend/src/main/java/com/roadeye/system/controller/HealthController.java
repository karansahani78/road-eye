package com.roadeye.system.controller;

import com.roadeye.shared.ApiResponse;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final DataSource dataSource;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    public HealthController(DataSource dataSource, MinioClient minioClient) {
        this.dataSource = dataSource;
        this.minioClient = minioClient;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", Instant.now());

        boolean dbHealthy = checkDatabase();
        details.put("database", Map.of("status", dbHealthy ? "UP" : "DOWN"));

        boolean kafkaHealthy = true;
        details.put("kafka", Map.of("status", kafkaHealthy ? "UP" : "DOWN"));

        boolean minioHealthy = checkMinio();
        details.put("minio", Map.of("status", minioHealthy ? "UP" : "DOWN"));

        String overallStatus = dbHealthy && minioHealthy ? "UP" : "DEGRADED";
        details.put("status", overallStatus);

        return ResponseEntity.ok(ApiResponse.success(details));
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("application", "RoadEye Backend");
        info.put("version", "0.1.0");
        info.put("javaVersion", Runtime.version().toString());
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("freeMemory", Runtime.getRuntime().freeMemory());
        info.put("maxMemory", Runtime.getRuntime().maxMemory());
        info.put("timestamp", Instant.now());
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkMinio() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            return false;
        }
    }
}
