package com.roadeye.system.controller;

import com.roadeye.shared.ApiResponse;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final KafkaAdmin kafkaAdmin;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public HealthController(DataSource dataSource, MinioClient minioClient,
                            KafkaAdmin kafkaAdmin, RestTemplate restTemplate) {
        this.dataSource = dataSource;
        this.minioClient = minioClient;
        this.kafkaAdmin = kafkaAdmin;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> details = new HashMap<>();
        details.put("timestamp", Instant.now());

        boolean dbHealthy = checkDatabase();
        details.put("database", Map.of("status", dbHealthy ? "UP" : "DOWN"));

        boolean kafkaHealthy = checkKafka();
        details.put("kafka", Map.of("status", kafkaHealthy ? "UP" : "DOWN"));

        boolean minioHealthy = checkMinio();
        details.put("minio", Map.of("status", minioHealthy ? "UP" : "DOWN"));

        boolean aiHealthy = checkAiEngine();
        details.put("aiEngine", Map.of("status", aiHealthy ? "UP" : "DOWN"));

        String overallStatus = (dbHealthy && kafkaHealthy && minioHealthy && aiHealthy) ? "UP" : "DEGRADED";
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

    private boolean checkKafka() {
        try {
            Properties props = new Properties();
            props.putAll(kafkaAdmin.getConfigurationProperties());
            try (AdminClient admin = AdminClient.create(props)) {
                var desc = admin.describeCluster(new DescribeClusterOptions().timeoutMs(3000));
                desc.clusterId().get(3, TimeUnit.SECONDS);
                return true;
            }
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

    private boolean checkAiEngine() {
        try {
            String url = aiServiceUrl + "/health";
            var response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    @Value("${minio.bucket}")
    private String bucketName;
}
