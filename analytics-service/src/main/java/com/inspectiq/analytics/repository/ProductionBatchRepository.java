package com.inspectiq.analytics.repository;

import com.inspectiq.analytics.entity.ProductionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Read-only access to production batches. Used to validate batch filters
 * (404 for unknown ids) and to count batches for the dashboard summary.
 */
public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, UUID> {
}