package com.inspectiq.ingestion.controller;

import com.inspectiq.ingestion.dto.BatchDtos.BatchListItem;
import com.inspectiq.ingestion.dto.BatchDtos.BatchResponse;
import com.inspectiq.ingestion.dto.BatchDtos.CreateBatchRequest;
import com.inspectiq.ingestion.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Batches", description = "Production batch management")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    @Operation(summary = "Create a production batch (requires X-Device-Api-Key header)")
    public ResponseEntity<BatchResponse> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        BatchResponse response = batchService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a batch with its pass/fail summary stats")
    public BatchResponse getBatch(@PathVariable UUID id) {
        return batchService.getBatch(id);
    }

    @GetMapping
    @Operation(summary = "List production batches with pass/fail summary stats (paginated)")
    public Page<BatchListItem> listBatches(Pageable pageable) {
        return batchService.listBatches(pageable);
    }
}