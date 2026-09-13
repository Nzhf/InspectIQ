package com.inspectiq.analytics.repository;

import com.inspectiq.analytics.entity.InspectionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * JPA access to inspection results for the simple "latest rows" query.
 * Heavy aggregations deliberately live in AnalyticsService (JdbcTemplate)
 * because they use Postgres-only SQL that JPQL cannot express.
 */
public interface InspectionResultRepository extends JpaRepository<InspectionResult, UUID> {

    /**
     * Latest inspections first. The batch and defect type are fetched eagerly so
     * the response mapping does not trigger extra per-row SELECTs.
     */
    @Query("""
            SELECT r FROM InspectionResult r
            JOIN FETCH r.batch
            LEFT JOIN FETCH r.defectType
            ORDER BY r.inspectedAt DESC
            """)
    Page<InspectionResult> findRecent(Pageable pageable);
}