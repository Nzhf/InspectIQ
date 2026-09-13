package com.inspectiq.analytics.controller;

import com.inspectiq.analytics.dto.AnalyticsDtos.RecentInspection;
import com.inspectiq.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inspections")
@Tag(name = "Inspections", description = "Read-side inspection queries for the dashboard")
public class InspectionQueryController {

    private final AnalyticsService analyticsService;

    public InspectionQueryController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/recent")
    @Operation(summary = "Latest inspection results for the dashboard table")
    public List<RecentInspection> recent(
            @RequestParam(defaultValue = "20") int limit) {
        return analyticsService.recentInspections(limit);
    }
}