package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.AlertMessage;
import com.inspectiq.alert.dto.AlertDtos.AlertState;
import com.inspectiq.alert.dto.AlertDtos.AlertStatus;
import com.inspectiq.alert.dto.AlertDtos.TelegramSendResult;
import com.inspectiq.alert.dto.AlertDtos.WindowYield;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically measures production yield over a rolling window of the most
 * recent N inspections and drives the alert state machine:
 *
 * <pre>
 *   OK ──(yield &lt; threshold)──▶ ALERT   ← one breach message
 *   ALERT ──(yield &gt;= threshold)──▶ OK   ← one recovery message
 * </pre>
 *
 * While in ALERT no message is re-sent (anti-spam); the identical transition
 * may only fire again after leaving ALERT. Windows smaller than
 * {@code minInspections} never alert — they are reported as INSUFFICIENT_DATA
 * and the internal state is preserved so a temporary data gap cannot cause a
 * duplicate breach or a lost recovery. All state fields are volatile because
 * the scheduler thread writes them while web threads read {@link #status()};
 * {@link #evaluateNow()} is synchronized so concurrent manual checks cannot
 * interleave transitions.
 */
@Service
public class YieldAlertMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(YieldAlertMonitor.class);

    private final YieldMetricsQuery metricsQuery;
    private final AlertMessageBuilder messageBuilder;
    private final TelegramNotifier telegramNotifier;

    private final double yieldThresholdPercent;
    private final int lookbackInspections;
    private final int minInspections;

    // --- Mutable monitor state (volatile: read from web threads) ---------------
    private volatile AlertState state = AlertState.OK;
    private volatile OffsetDateTime lastCheckedAt;
    private volatile Integer lastSampleUnits;
    private volatile Double lastYieldPercent;
    private volatile OffsetDateTime lastAlertAt;
    private volatile String lastAlertType;
    private final AtomicBoolean disabledLogged = new AtomicBoolean(false);

    public YieldAlertMonitor(
            YieldMetricsQuery metricsQuery,
            AlertMessageBuilder messageBuilder,
            TelegramNotifier telegramNotifier,
            @Value("${inspectiq.alert.yield-threshold-percent:95.0}") double yieldThresholdPercent,
            @Value("${inspectiq.alert.lookback-inspections:200}") int lookbackInspections,
            @Value("${inspectiq.alert.min-inspections:20}") int minInspections) {
        this.metricsQuery = metricsQuery;
        this.messageBuilder = messageBuilder;
        this.telegramNotifier = telegramNotifier;
        this.yieldThresholdPercent = yieldThresholdPercent;
        this.lookbackInspections = lookbackInspections;
        this.minInspections = minInspections;
    }

    /** Scheduled tick; independently invokable via POST /api/v1/alerts/check. */
    @Scheduled(fixedDelayString = "${inspectiq.alert.check-interval-ms:60000}")
    public void tick() {
        evaluateNow();
    }

    /**
     * Re-reads the rolling window and drives the state machine.
     *
     * @return the newly evaluated state (INSUFFICIENT_DATA when the sample is
     *         smaller than {@code minInspections}, otherwise OK or ALERT)
     */
    public synchronized AlertState evaluateNow() {
        OffsetDateTime checkedAt = OffsetDateTime.now();
        Optional<WindowYield> maybeWindow = metricsQuery.recentWindowYield(lookbackInspections);

        if (maybeWindow.isEmpty()) {
            lastCheckedAt = checkedAt;
            lastSampleUnits = 0;
            lastYieldPercent = null;
            LOGGER.info("No inspection data yet — state remains {}", state);
            return AlertState.INSUFFICIENT_DATA;
        }

        WindowYield window = maybeWindow.get();
        lastCheckedAt = checkedAt;
        lastSampleUnits = saturatedInt(window.inspectedUnits());
        lastYieldPercent = window.inspectedUnits() > 0
                ? (window.passCount() * 100.0) / window.inspectedUnits()
                : null;

        if (window.inspectedUnits() < minInspections) {
            LOGGER.info("Rolling window too small ({} < {} inspections) — no alert, state preserved",
                    window.inspectedUnits(), minInspections);
            return AlertState.INSUFFICIENT_DATA;
        }

        double yieldPercent = (window.passCount() * 100.0) / window.inspectedUnits();
        if (yieldPercent < yieldThresholdPercent) {
            if (state != AlertState.ALERT) {
                state = AlertState.ALERT;
                dispatch(messageBuilder.breach(window, lookbackInspections, yieldThresholdPercent, checkedAt),
                        "BREACH");
            }
        } else if (state == AlertState.ALERT) {
            state = AlertState.OK;
            dispatch(messageBuilder.recovery(window, lookbackInspections, yieldThresholdPercent, checkedAt),
                    "RECOVERY");
        }
        return state;
    }

    /** Immutable snapshot for consumers (controller / status endpoint). */
    public AlertStatus status() {
        AlertState reportedState = lastSampleUnits != null && lastSampleUnits >= minInspections
                ? state
                : AlertState.INSUFFICIENT_DATA;
        return new AlertStatus(
                telegramNotifier.isEnabled(),
                yieldThresholdPercent,
                lookbackInspections,
                minInspections,
                reportedState,
                lastCheckedAt,
                lastSampleUnits,
                lastYieldPercent,
                lastAlertAt,
                lastAlertType);
    }

    // --- helpers ----------------------------------------------------------------

    /**
     * Sends one transition message. Only successful sends are recorded as the
     * "last alert" (an unreachable/misconfigured Telegram must not claim a
     * notification went out). A disabled notifier is logged exactly once.
     */
    private void dispatch(AlertMessage message, String alertType) {
        TelegramSendResult result = telegramNotifier.send(message);
        if (result == TelegramSendResult.SENT) {
            lastAlertAt = OffsetDateTime.now();
            lastAlertType = alertType;
        } else if (result == TelegramSendResult.DISABLED && disabledLogged.compareAndSet(false, true)) {
            LOGGER.warn("Telegram is not configured — keep evaluating, notifications are skipped");
        }
    }

    private static int saturatedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}