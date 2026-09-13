package com.inspectiq.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class BatchDtos {

    private BatchDtos() {
        // Namespace for batch-related request/response records
    }

    /** Payload for POST /api/v1/batches. */
    public record CreateBatchRequest(
            @NotBlank(message = "batchCode is required")
            @Size(max = 255, message = "batchCode must not exceed 255 characters")
            String batchCode,
            @NotBlank(message = "productName is required")
            @Size(max = 255, message = "productName must not exceed 255 characters")
            String productName,
            @NotBlank(message = "status is required")
            @Pattern(regexp = "IN_PROGRESS|COMPLETED|ON_HOLD", flags = Pattern.Flag.CASE_INSENSITIVE,
                    message = "status must be one of IN_PROGRESS, COMPLETED, ON_HOLD")
            String status) {
    }

    /** Response for a batch, including pass/fail summary stats. */
    public record BatchResponse(
            UUID id,
            String batchCode,
            String productName,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            String status,
            long totalInspections,
            long passCount,
            long failCount,
            Double passRatePercent) {
    }
}