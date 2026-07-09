package com.roadeye.evidence.service;

import com.roadeye.shared.ResourceNotFoundException;
import com.roadeye.violation.entity.ViolationEvidence;
import com.roadeye.violation.repository.ViolationEvidenceRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

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

    @Transactional
    public ViolationEvidence storeEvidence(String violationId, String evidenceType, byte[] data,
                                            String contentType, String objectKey, String bucketName) {
        String bucket = bucketName != null ? bucketName : defaultBucket;

        String sha256 = computeSha256(data);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to store evidence in MinIO: " + e.getMessage(), e);
        }

        ViolationEvidence evidence = new ViolationEvidence();
        evidence.setViolationId(violationId);
        evidence.setEvidenceType(evidenceType);
        evidence.setBucketName(bucket);
        evidence.setObjectKey(objectKey);
        evidence.setFileSize((long) data.length);
        evidence.setContentType(contentType);
        evidence.setChecksum("SHA-256:" + sha256);
        evidence = evidenceRepository.save(evidence);

        log.info("Stored evidence {} (SHA-256: {}) for violation {}", evidence.getId(), sha256, violationId);
        return evidence;
    }

    public boolean verifyIntegrity(UUID evidenceId) {
        ViolationEvidence evidence = getEvidence(evidenceId);
        String checksum = evidence.getChecksum();
        if (checksum == null || !checksum.startsWith("SHA-256:")) {
            log.warn("Evidence {} has no SHA-256 checksum, cannot verify", evidenceId);
            return false;
        }
        String expectedHash = checksum.substring(7);
        try (InputStream stream = getObjectStream(evidence)) {
            String actualHash = computeSha256(stream);
            boolean match = expectedHash.equals(actualHash);
            if (!match) {
                log.error("INTEGRITY FAILURE: Evidence {} hash mismatch. Expected: {}, Actual: {}",
                        evidenceId, expectedHash, actualHash);
            }
            return match;
        } catch (Exception e) {
            log.error("Failed to verify evidence {} integrity", evidenceId, e);
            return false;
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String computeSha256(InputStream data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = data.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
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
