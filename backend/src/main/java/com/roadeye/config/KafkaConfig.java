package com.roadeye.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_CAMERA_HEALTH = "camera-health";
    public static final String TOPIC_VEHICLE_OBSERVATIONS = "vehicle-observations";
    public static final String TOPIC_PLATE_RECOGNITIONS = "plate-recognitions";
    public static final String TOPIC_SPEED_ESTIMATIONS = "speed-estimations";
    public static final String TOPIC_VIOLATION_CANDIDATES = "violation-candidates";
    public static final String TOPIC_EVIDENCE_CREATED = "evidence-created";

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public NewTopic cameraHealthTopic() {
        return TopicBuilder.name(TOPIC_CAMERA_HEALTH)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic vehicleObservationsTopic() {
        return TopicBuilder.name(TOPIC_VEHICLE_OBSERVATIONS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic plateRecognitionsTopic() {
        return TopicBuilder.name(TOPIC_PLATE_RECOGNITIONS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic speedEstimationsTopic() {
        return TopicBuilder.name(TOPIC_SPEED_ESTIMATIONS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic violationCandidatesTopic() {
        return TopicBuilder.name(TOPIC_VIOLATION_CANDIDATES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic evidenceCreatedTopic() {
        return TopicBuilder.name(TOPIC_EVIDENCE_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
