package com.roadeye.evidence.service;

import com.roadeye.shared.ResourceNotFoundException;
import com.roadeye.violation.entity.ViolationEvidence;
import com.roadeye.violation.repository.ViolationEvidenceRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EvidenceService {

    private final ViolationEvidenceRepository evidenceRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String defaultBucket;

    public EvidenceService(ViolationEvidenceRepository evidenceRepository, MinioClient minioClient) {
        this.evidenceRepository = evidenceRepository;
        this.minioClient = minioClient;
    }

    public ViolationEvidence getEvidence(UUID id) {
        return evidenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", "id", id));
    }

    public ResponseEntity<InputStreamResource> streamFile(UUID id) {
        ViolationEvidence evidence = getEvidence(id);
        InputStream stream = getObjectStream(evidence);
        String contentType = evidence.getContentType() != null
                ? evidence.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(stream));
    }

    public ResponseEntity<InputStreamResource> downloadFile(UUID id) {
        ViolationEvidence evidence = getEvidence(id);
        InputStream stream = getObjectStream(evidence);
        String filename = evidence.getObjectKey() != null
                ? evidence.getObjectKey().substring(evidence.getObjectKey().lastIndexOf('/') + 1)
                : evidence.getId() + ".bin";
        String contentType = evidence.getContentType() != null
                ? evidence.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new InputStreamResource(stream));
    }

    private InputStream getObjectStream(ViolationEvidence evidence) {
        String bucket = evidence.getBucketName() != null ? evidence.getBucketName() : defaultBucket;
        String objectKey = evidence.getObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new ResourceNotFoundException("Evidence file", "evidenceId", evidence.getId());
        }
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new ResourceNotFoundException("Evidence file", "objectKey", objectKey);
        }
    }
}
