package com.inspectiq.analytics.controller;

import com.inspectiq.analytics.dto.AnalyticsDtos.DefectDistributionItem;
import com.inspectiq.analytics.dto.AnalyticsDtos.YieldPoint;
import com.inspectiq.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "Metrics", description = "Aggregated quality metrics for charts")
public class MetricsController {

    private final AnalyticsService analyticsService;

    public MetricsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/yield")
    @Operation(summary = "Yield trend (line chart): grouped by day or by batch")
    public List<YieldPoint> yield(
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return analyticsService.yieldOverTime(groupBy, from, to);
    }

    @GetMapping("/defects")
    @Operation(summary = "Defect-type distribution (pie chart) for failed inspections")
    public List<DefectDistributionItem> defects(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return analyticsService.defectDistribution(batchId, from, to);
    }
}