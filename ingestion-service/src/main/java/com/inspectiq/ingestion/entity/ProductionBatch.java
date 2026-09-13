package com.inspectiq.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A production run (one row per batch). Maps to production_batches (V1__init_schema.sql).
 * The DB column defaults are kept as a safety net, but started_at is set in Java so
 * the create response always carries a value.
 */
@Entity
@Table(name = "production_batches")
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable identifier. Case-insensitive uniqueness is enforced in the service layer. */
    @Column(name = "batch_code", nullable = false)
    private String batchCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** IN_PROGRESS, COMPLETED or ON_HOLD. */
    @Column(nullable = false)
    private String status;

    protected ProductionBatch() {
        // Required by JPA
    }

    public ProductionBatch(String batchCode, String productName, String status) {
        this.batchCode = batchCode;
        this.productName = productName;
        this.status = status;
        this.startedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public String getProductName() {
        return productName;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getStatus() {
        return status;
    }
}