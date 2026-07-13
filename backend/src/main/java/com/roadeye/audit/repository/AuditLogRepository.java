package com.roadeye.audit.repository;

import com.roadeye.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByTargetType(String targetType, Pageable pageable);

    Page<AuditLog> findByActor(String actor, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(Instant start, Instant end, Pageable pageable);

    Page<AuditLog> findByActionAndCreatedAtBetween(String action, Instant start, Instant end, Pageable pageable);

    Page<AuditLog> findByTargetTypeAndCreatedAtBetween(String targetType, Instant start, Instant end, Pageable pageable);

    Page<AuditLog> findByActorAndCreatedAtBetween(String actor, Instant start, Instant end, Pageable pageable);

    void deleteByCreatedAtBefore(Instant cutoff);

    long countByCreatedAtBefore(Instant cutoff);
}
