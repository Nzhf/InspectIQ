package com.inspectiq.ingestion.service;

import com.inspectiq.ingestion.dto.BatchDtos.BatchListItem;
import com.inspectiq.ingestion.dto.BatchDtos.BatchResponse;
import com.inspectiq.ingestion.dto.BatchDtos.CreateBatchRequest;
import com.inspectiq.ingestion.entity.ProductionBatch;
import com.inspectiq.ingestion.exception.ConflictException;
import com.inspectiq.ingestion.exception.NotFoundException;
import com.inspectiq.ingestion.repository.InspectionResultRepository;
import com.inspectiq.ingestion.repository.ProductionBatchRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BatchService {

    private final ProductionBatchRepository batchRepository;
    private final InspectionResultRepository inspectionRepository;

    public BatchService(ProductionBatchRepository batchRepository, InspectionResultRepository inspectionRepository) {
        this.batchRepository = batchRepository;
        this.inspectionRepository = inspectionRepository;
    }

    @Transactional
    public BatchResponse createBatch(CreateBatchRequest request) {
        String normalizedBatchCode = request.batchCode().trim();
        if (batchRepository.existsByBatchCodeIgnoreCase(normalizedBatchCode)) {
            throw new ConflictException("A batch with code '" + normalizedBatchCode + "' already exists");
        }

        ProductionBatch batch = new ProductionBatch(
                normalizedBatchCode,
                request.productName().trim(),
                request.status().toUpperCase());

        try {
            batch = batchRepository.save(batch);
        } catch (DataIntegrityViolationException ex) {
            // Handles the race where two stations create the same code between
            // the exists-check above and this insert (the DB UNIQUE constraint wins).
            throw new ConflictException("A batch with code '" + normalizedBatchCode + "' already exists");
        }

        return toResponse(batch, 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID id) {
        ProductionBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No batch exists with id " + id));

        long totalInspections = inspectionRepository.countByBatchId(id);
        long passCount = inspectionRepository.countByBatchIdAndResult(id, "PASS");
        long failCount = totalInspections - passCount;
        return toResponse(batch, totalInspections, passCount, failCount);
    }

    @Transactional(readOnly = true)
    public Page<BatchListItem> listBatches(Pageable pageable) {
        return batchRepository.findAllWithCounts(pageable);
    }

    private BatchResponse toResponse(ProductionBatch batch, long totalInspections, long passCount, long failCount) {
        Double passRatePercent = null;
        if (totalInspections > 0) {
            passRatePercent = (passCount * 100.0) / totalInspections;
        }
        return new BatchResponse(
                batch.getId(),
                batch.getBatchCode(),
                batch.getProductName(),
                batch.getStartedAt(),
                batch.getCompletedAt(),
                batch.getStatus(),
                totalInspections,
                passCount,
                failCount,
                passRatePercent);
    }
}