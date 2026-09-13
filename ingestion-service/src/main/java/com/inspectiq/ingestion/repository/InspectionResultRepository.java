package com.inspectiq.ingestion.repository;

import com.inspectiq.ingestion.entity.InspectionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, UUID> {

    /**
     * Filter by any combination of batch and inspected-at time range.
     * Derived queries cannot ignore null parameters, so this uses an explicit
     * JPQL query with IS NULL guards — batch only, time range only, both, or
     * neither all behave correctly.
     */
    @Query("""
            SELECT r FROM InspectionResult r
            WHERE (:batchId IS NULL OR r.batch.id = :batchId)
              AND (:from IS NULL OR r.inspectedAt >= :from)
              AND (:to IS NULL OR r.inspectedAt <= :to)
            """)
    Page<InspectionResult> search(
            @Param("batchId") UUID batchId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    long countByBatchIdAndResult(UUID batchId, String result);

    long countByBatchId(UUID batchId);
}