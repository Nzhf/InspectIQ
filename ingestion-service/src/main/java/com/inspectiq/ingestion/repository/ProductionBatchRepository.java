package com.inspectiq.ingestion.repository;

import com.inspectiq.ingestion.entity.ProductionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, UUID> {

    boolean existsByBatchCodeIgnoreCase(String batchCode);

    Optional<ProductionBatch> findByBatchCodeIgnoreCase(String batchCode);
}