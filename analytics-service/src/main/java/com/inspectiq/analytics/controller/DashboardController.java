package com.inspectiq.analytics.controller;

import com.inspectiq.analytics.dto.AnalyticsDtos.DashboardSummary;
import com.inspectiq.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "KPI summary for the dashboard header")
public class DashboardController {

    private final AnalyticsService analyticsService;

    public DashboardController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Overall quality KPIs (units, passes, fails, yield %, batch count)")
    public DashboardSummary summary() {
        return analyticsService.summary();
    }
}