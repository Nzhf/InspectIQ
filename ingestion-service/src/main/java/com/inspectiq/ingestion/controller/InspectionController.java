package com.inspectiq.ingestion.controller;

import com.inspectiq.ingestion.dto.InspectionDtos.CreateInspectionRequest;
import com.inspectiq.ingestion.dto.InspectionDtos.InspectionResponse;
import com.inspectiq.ingestion.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections")
@Tag(name = "Inspections", description = "Recording and querying AOI inspection results")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @PostMapping
    @Operation(summary = "Record a single inspection result (requires X-Device-Api-Key header)")
    public ResponseEntity<InspectionResponse> recordInspection(
            @Valid @RequestBody CreateInspectionRequest request) {
        InspectionResponse response = inspectionService.recordInspection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Query inspections with optional batch and time-range filters")
    public Page<InspectionResponse> searchInspections(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inspectionService.searchInspections(batchId, from, to, page, size);
    }
}