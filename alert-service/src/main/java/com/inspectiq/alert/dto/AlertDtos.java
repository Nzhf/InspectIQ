package com.inspectiq.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Namespace for alert-service request/response types (mirrors AnalyticsDtos).
 * Records live here so the wire contract is easy to audit in one place.
 */
public final class AlertDtos {

    private AlertDtos() {
        // Namespace for alert-service records
    }

    /** A single yield sample over the rolling lookback window. */
    public record WindowYield(long inspectedUnits, long passCount) {
    }

    /** Current state of the alert state machine (OK → ALERT → OK). */
    public enum AlertState {
        OK,
        ALERT,
        INSUFFICIENT_DATA
    }

    /** Snapshot exposed via GET /api/v1/alerts/status. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AlertStatus(
            boolean telegramEnabled,
            double yieldThresholdPercent,
            int lookbackInspections,
            int minInspections,
            AlertState state,
            OffsetDateTime lastCheckedAt,
            Integer lastSampleUnits,
            Double lastYieldPercent,
            OffsetDateTime lastAlertAt,
            String lastAlertType) {
    }

    /** A formatted notification produced by the builder and sent by the notifier. */
    public record AlertMessage(String text, boolean isBreach) {
    }

    /** Outcome of a Telegram send attempt. */
    public enum TelegramSendResult {
        SENT,
        DISABLED,
        FAILED
    }
}