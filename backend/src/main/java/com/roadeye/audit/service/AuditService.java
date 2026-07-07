package com.roadeye.audit.service;

import com.roadeye.audit.entity.AuditLog;
import com.roadeye.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@Transactional
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void log(String actor, String action, String targetType, String targetId, Map<String, Object> metadata) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setActor(actor);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);

            if (metadata != null && !metadata.isEmpty()) {
                auditLog.setMetadata(objectMapper.writeValueAsString(metadata));
            }

            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit metadata", e);
        }
    }

    @Async
    public void log(String actor, String action, String targetType, String targetId) {
        log(actor, action, targetType, targetId, null);
    }

    @Async
    public void log(String actor, String action) {
        log(actor, action, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(String action, String targetType, String actor,
                                       Instant startDate, Instant endDate, Pageable pageable) {
        if (action != null && startDate != null && endDate != null) {
            return auditLogRepository.findByActionAndCreatedAtBetween(action, startDate, endDate, pageable);
        }
        if (targetType != null && startDate != null && endDate != null) {
            return auditLogRepository.findByTargetTypeAndCreatedAtBetween(targetType, startDate, endDate, pageable);
        }
        if (actor != null && startDate != null && endDate != null) {
            return auditLogRepository.findByActorAndCreatedAtBetween(actor, startDate, endDate, pageable);
        }
        if (action != null) {
            return auditLogRepository.findByAction(action, pageable);
        }
        if (targetType != null) {
            return auditLogRepository.findByTargetType(targetType, pageable);
        }
        if (actor != null) {
            return auditLogRepository.findByActor(actor, pageable);
        }
        if (startDate != null && endDate != null) {
            return auditLogRepository.findByCreatedAtBetween(startDate, endDate, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }
}
