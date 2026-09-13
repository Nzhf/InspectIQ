package com.inspectiq.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
        // Namespace for analytics request/response records
    }

    /** Overall quality KPIs for the dashboard header. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSummary(
            long totalUnits,
            long totalPasses,
            long totalFails,
            Double yieldPercent,
            long totalBatches) {
    }

    /**
     * One yield sample on a trend line. The bucket is a chart label:
     * an ISO day ("2026-09-13") for groupBy=day, or a batch code for groupBy=batch.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record YieldPoint(
            String bucket,
            long totalUnits,
            long passCount,
            long failCount,
            Double yieldPercent) {
    }

    /** One slice of a defect-type pie chart (percent is share of failed units). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DefectDistributionItem(
            String code,
            String description,
            long count,
            Double percentOfFails) {
    }

    /** Latest inspection rows for the dashboard table. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentInspection(
            UUID id,
            UUID batchId,
            String batchCode,
            String result,
            OffsetDateTime inspectedAt,
            String defectTypeCode) {
    }
}