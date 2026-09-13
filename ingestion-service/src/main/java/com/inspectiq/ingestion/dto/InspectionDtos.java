package com.inspectiq.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.inspectiq.ingestion.validation.DefectOnlyOnFail;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class InspectionDtos {

    private InspectionDtos() {
        // Namespace for inspection-related request/response records
    }

    /** Payload for POST /api/v1/inspections. */
    @DefectOnlyOnFail
    public record CreateInspectionRequest(
            @NotNull(message = "batchId is required") UUID batchId,
            @NotNull(message = "result is required")
            @Pattern(regexp = "PASS|FAIL", flags = Pattern.Flag.CASE_INSENSITIVE,
                    message = "result must be PASS or FAIL")
            String result,
            @Size(max = 100, message = "defectTypeCode must not exceed 100 characters")
            String defectTypeCode,
            Map<String, Object> rawData) {
    }

    /** Response for a single inspection result. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InspectionResponse(
            UUID id,
            UUID batchId,
            String result,
            OffsetDateTime inspectedAt,
            String defectTypeCode,
            Map<String, Object> rawData) {
    }
}