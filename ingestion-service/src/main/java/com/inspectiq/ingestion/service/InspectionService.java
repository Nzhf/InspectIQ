package com.inspectiq.ingestion.service;

import com.inspectiq.ingestion.dto.InspectionDtos.CreateInspectionRequest;
import com.inspectiq.ingestion.dto.InspectionDtos.InspectionResponse;
import com.inspectiq.ingestion.entity.DefectType;
import com.inspectiq.ingestion.entity.InspectionResult;
import com.inspectiq.ingestion.entity.ProductionBatch;
import com.inspectiq.ingestion.exception.NotFoundException;
import com.inspectiq.ingestion.repository.DefectTypeRepository;
import com.inspectiq.ingestion.repository.InspectionResultRepository;
import com.inspectiq.ingestion.repository.ProductionBatchRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class InspectionService {

    /** Upper bound so a misbehaving client cannot request unbounded pages. */
    private static final int MAX_PAGE_SIZE = 200;

    private final InspectionResultRepository inspectionRepository;
    private final ProductionBatchRepository batchRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final ObjectMapper objectMapper;

    public InspectionService(
            InspectionResultRepository inspectionRepository,
            ProductionBatchRepository batchRepository,
            DefectTypeRepository defectTypeRepository,
            ObjectMapper objectMapper) {
        this.inspectionRepository = inspectionRepository;
        this.batchRepository = batchRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InspectionResponse recordInspection(CreateInspectionRequest request) {
        ProductionBatch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new NotFoundException("No batch exists with id " + request.batchId()));

        DefectType defectType = null;
        if (request.defectTypeCode() != null && !request.defectTypeCode().isBlank()) {
            String normalizedCode = request.defectTypeCode().trim().toUpperCase();
            defectType = defectTypeRepository.findByCodeIgnoreCase(normalizedCode)
                    .orElseThrow(() -> new NotFoundException("Unknown defect type code '" + normalizedCode + "'"));
        }

        InspectionResult inspection = new InspectionResult(
                batch,
                request.result().toUpperCase(),
                defectType,
                serializeRawData(request.rawData()));
        inspection = inspectionRepository.save(inspection);

        return toResponse(inspection);
    }

    @Transactional(readOnly = true)
    public Page<InspectionResponse> searchInspections(UUID batchId, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "inspectedAt"));
        return inspectionRepository.search(batchId, from, to, pageable)
                .map(this::toResponse);
    }

    private String serializeRawData(java.util.Map<String, Object> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rawData);
        } catch (JsonProcessingException ex) {
            // rawData comes from Jackson in the first place, so this should be
            // unreachable; fail loudly rather than silently dropping station data.
            throw new IllegalStateException("Could not serialize rawData to JSON", ex);
        }
    }

    private InspectionResponse toResponse(InspectionResult inspection) {
        String defectTypeCode = inspection.getDefectType() != null ? inspection.getDefectType().getCode() : null;
        return new InspectionResponse(
                inspection.getId(),
                inspection.getBatch().getId(),
                inspection.getResult(),
                inspection.getInspectedAt(),
                defectTypeCode,
                inspection.getRawData());
    }
}