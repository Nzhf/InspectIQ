package com.inspectiq.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only view of one row per unit inspected (inspection_results, V1__init_schema.sql).
 * Intentionally lighter than ingestion's write-side entity: analytics only needs the
 * batch, result, time and defect reference — raw station payloads are not exposed here.
 */
@Entity
@Immutable
@Table(name = "inspection_results")
public class InspectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    /** PASS or FAIL. */
    @Column(nullable = false)
    private String result;

    @Column(name = "inspected_at", nullable = false)
    private OffsetDateTime inspectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_type_id")
    private DefectType defectType;

    protected InspectionResult() {
        // Required by JPA
    }

    public InspectionResult(ProductionBatch batch, String result, DefectType defectType) {
        this.batch = batch;
        this.result = result;
        this.defectType = defectType;
        this.inspectedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public String getResult() {
        return result;
    }

    public OffsetDateTime getInspectedAt() {
        return inspectedAt;
    }

    public DefectType getDefectType() {
        return defectType;
    }
}