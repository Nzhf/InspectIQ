package com.inspectiq.alert.controller;

import com.inspectiq.alert.dto.AlertDtos.AlertStatus;
import com.inspectiq.alert.service.YieldAlertMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Yield threshold monitoring and Telegram notifications")
public class AlertController {

    private final YieldAlertMonitor alertMonitor;

    public AlertController(YieldAlertMonitor alertMonitor) {
        this.alertMonitor = alertMonitor;
    }

    @GetMapping("/status")
    @Operation(summary = "Current alert state, thresholds, last sample and last alert")
    public AlertStatus status() {
        return alertMonitor.status();
    }

    @PostMapping("/check")
    @Operation(summary = "Trigger a manual re-evaluation of the rolling yield window (same as the scheduled tick)")
    public AlertStatus check() {
        alertMonitor.evaluateNow();
        return alertMonitor.status();
    }
}