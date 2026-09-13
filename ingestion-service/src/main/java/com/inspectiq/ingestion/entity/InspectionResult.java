package com.inspectiq.ingestion.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per unit inspected. Maps to inspection_results (V1__init_schema.sql).
 * The DB CHECK constraint (chk_defect_only_on_fail) enforces that a PASS
 * inspection has no defect type; the service layer validates this too so
 * callers get a clean 400 instead of a DB error.
 */
@Entity
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

    /**
     * Free-form station payload (confidence score, sensor readings, ...).
     * SqlTypes.JSON makes Hibernate treat the String as JSON so schema
     * validation accepts the jsonb column instead of expecting varchar.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    protected InspectionResult() {
        // Required by JPA
    }

    public InspectionResult(ProductionBatch batch, String result, DefectType defectType, String rawData) {
        this.batch = batch;
        this.result = result;
        this.defectType = defectType;
        this.rawData = rawData;
        // Set in Java (not the DB default) so the create response carries a value
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

    public String getRawData() {
        return rawData;
    }
}