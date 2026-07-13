package com.roadeye.violation.repository;

import com.roadeye.violation.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {
    Page<Violation> findByStatus(String status, Pageable pageable);
    Page<Violation> findByViolationType(String violationType, Pageable pageable);
    Page<Violation> findByCameraId(String cameraId, Pageable pageable);
    Page<Violation> findByCameraIdAndViolatedAtBetween(String cameraId, Instant start, Instant end, Pageable pageable);

    long countByStatus(String status);

    long countByStatusNot(String status);

    long countByViolatedAtBetween(Instant start, Instant end);

    long countByViolationTypeAndViolatedAtBetween(String violationType, Instant start, Instant end);

    long countByCameraIdAndViolatedAtAfter(String cameraId, Instant after);

    @Query("SELECT v FROM Violation v WHERE v.violatedAt >= :after ORDER BY v.violatedAt DESC")
    List<Violation> findRecentViolations(@Param("after") Instant after);

    @Query("SELECT FUNCTION('date_trunc', 'day', v.violatedAt) AS day, COUNT(v) FROM Violation v WHERE v.violatedAt BETWEEN :start AND :end GROUP BY day ORDER BY day")
    List<Object[]> countByDayBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT FUNCTION('date_trunc', 'day', v.violatedAt) AS day, COUNT(v) FROM Violation v WHERE v.violationType = :type AND v.violatedAt BETWEEN :start AND :end GROUP BY day ORDER BY day")
    List<Object[]> countByTypeAndDayBetween(@Param("type") String type, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT v.status, COUNT(v) FROM Violation v GROUP BY v.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT v.cameraId, COUNT(v) FROM Violation v WHERE v.cameraId IN :cameraIds AND v.violatedAt >= :after GROUP BY v.cameraId")
    List<Object[]> countByCameraIdsAndViolatedAtAfter(@Param("cameraIds") List<String> cameraIds, @Param("after") Instant after);
}
