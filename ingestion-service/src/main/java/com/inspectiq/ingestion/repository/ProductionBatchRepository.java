package com.inspectiq.ingestion.repository;

import com.inspectiq.ingestion.dto.BatchDtos.BatchListItem;
import com.inspectiq.ingestion.entity.ProductionBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, UUID> {

    boolean existsByBatchCodeIgnoreCase(String batchCode);

    Optional<ProductionBatch> findByBatchCodeIgnoreCase(String batchCode);

    /**
     * Paginated batch list with pass/fail counts computed via correlated subqueries.
     * Subqueries (rather than a JOIN + GROUP BY) keep the query portable across
     * JPA providers and let Spring Data JPA derive the count query for pagination
     * automatically. The pass rate is computed here so the service layer receives
     * a fully-hydrated record with no post-processing.
     */
    @Query("""
            SELECT new com.inspectiq.ingestion.dto.BatchDtos.BatchListItem(
                b.id,
                b.batchCode,
                b.productName,
                b.startedAt,
                b.completedAt,
                b.status,
                (SELECT COUNT(i.id) FROM com.inspectiq.ingestion.entity.InspectionResult i WHERE i.batch.id = b.id),
                (SELECT COUNT(i.id) FROM com.inspectiq.ingestion.entity.InspectionResult i WHERE i.batch.id = b.id AND i.result = 'PASS'),
                (SELECT COUNT(i.id) FROM com.inspectiq.ingestion.entity.InspectionResult i WHERE i.batch.id = b.id AND i.result = 'FAIL'),
                CASE WHEN (SELECT COUNT(i.id) FROM com.inspectiq.ingestion.entity.InspectionResult i WHERE i.batch.id = b.id) > 0
                     THEN ((SELECT COUNT(i.id) FROM com.inspectiq.ingestion.entity.InspectionResult i WHERE i.batch.id = b.id AND i.result = 'PASS') * 100.0)
                          / (SELECT COUNT(i.id) FROM com.inspectiq.ingestion.entity.InspectionResult i WHERE i.batch.id = b.id)
                     ELSE NULL END
            )
            FROM ProductionBatch b
            ORDER BY b.startedAt DESC
            """)
    Page<BatchListItem> findAllWithCounts(Pageable pageable);
}