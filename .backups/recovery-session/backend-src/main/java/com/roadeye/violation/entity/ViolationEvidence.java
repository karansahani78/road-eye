package com.roadeye.violation.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "violation_evidence", indexes = {
    @Index(name = "idx_ve_violation", columnList = "violation_id")
})
public class ViolationEvidence extends BaseEntity {
    @Column(name = "violation_id", nullable = false)
    private String violationId;

    @Column(name = "evidence_type", nullable = false, length = 50)
    private String evidenceType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "bucket_name")
    private String bucketName;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
