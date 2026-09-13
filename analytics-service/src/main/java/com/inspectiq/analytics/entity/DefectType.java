package com.inspectiq.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only lookup of standard defect categories (e.g. SOLDER_BRIDGE, TOMBSTONE).
 * Seeded by V2__seed_defect_types.sql; referenced by failed inspections.
 */
@Entity
@Immutable
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

    public DefectType(String code, String description) {
        this.code = code;
        this.description = description;
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