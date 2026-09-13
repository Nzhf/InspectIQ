package com.inspectiq.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lookup of standard defect categories (e.g. SOLDER_BRIDGE, TOMBSTONE).
 * Seeded by V2__seed_defect_types.sql; referenced by failed inspections.
 */
@Entity
@Table(name = "defect_types")
public class DefectType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String description;

    protected DefectType() {
        // Required by JPA
    }

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}