package com.inspectiq.ingestion.repository;

import com.inspectiq.ingestion.entity.DefectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DefectTypeRepository extends JpaRepository<DefectType, Integer> {

    Optional<DefectType> findByCodeIgnoreCase(String code);
}