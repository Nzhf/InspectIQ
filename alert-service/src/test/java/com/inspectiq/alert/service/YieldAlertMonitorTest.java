package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.AlertMessage;
import com.inspectiq.alert.dto.AlertDtos.AlertState;
import com.inspectiq.alert.dto.AlertDtos.AlertStatus;
import com.inspectiq.alert.dto.AlertDtos.TelegramSendResult;
import com.inspectiq.alert.dto.AlertDtos.WindowYield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YieldAlertMonitorTest {

    private final YieldMetricsQuery metricsQuery = mock(YieldMetricsQuery.class);
    private final AlertMessageBuilder messageBuilder = mock(AlertMessageBuilder.class);
    private final TelegramNotifier telegramNotifier = mock(TelegramNotifier.class);

    private YieldAlertMonitor monitor;

    @BeforeEach
    void setUp() {
        when(telegramNotifier.isEnabled()).thenReturn(true);
        when(telegramNotifier.send(any())).thenReturn(TelegramSendResult.SENT);
        when(messageBuilder.breach(any(), anyInt(), anyDouble(), any()))
                .thenReturn(new AlertMessage("<b>breach</b>", true));
        when(messageBuilder.recovery(any(), anyInt(), anyDouble(), any()))
                .thenReturn(new AlertMessage("<b>recovery</b>", false));
        monitor = new YieldAlertMonitor(metricsQuery, messageBuilder, telegramNotifier, 95.0, 200, 20);
    }

    @Test
    void okToAlertSendsBreachExactlyOnceAndStaysAlertWhileBreaching() {
        when(metricsQuery.recentWindowYield(200))
                .thenReturn(Optional.of(new WindowYield(200, 180))); // 90.0% < 95.0%

        assertEquals(AlertState.ALERT, monitor.evaluateNow());
        assertEquals(AlertState.ALERT, monitor.evaluateNow()); // still breaching

        verify(messageBuilder, times(1)).breach(any(), anyInt(), anyDouble(), any());
        verify(messageBuilder, never()).recovery(any(), anyInt(), anyDouble(), any());
        verify(telegramNotifier, times(1)).send(any());

        AlertStatus status = monitor.status();
        assertEquals(AlertState.ALERT, status.state());
        assertEquals(200, status.lastSampleUnits());
        assertEquals(90.0, status.lastYieldPercent(), 0.0001);
        assertEquals("BREACH", status.lastAlertType());
        assertNotNull(status.lastAlertAt());
        assertTrue(status.telegramEnabled());
    }

    @Test
    void recoveryIsSentExactlyOnceWhenYieldReturnsAboveThreshold() {
        when(metricsQuery.recentWindowYield(200))
                .thenReturn(Optional.of(new WindowYield(200, 180)))  // 90.0% → ALERT
                .thenReturn(Optional.of(new WindowYield(200, 192))); // 96.0% → OK

        assertEquals(AlertState.ALERT, monitor.evaluateNow());
        assertEquals(AlertState.OK, monitor.evaluateNow());

        verify(messageBuilder, times(1)).breach(any(), anyInt(), anyDouble(), any());
        verify(messageBuilder, times(1)).recovery(any(), anyInt(), anyDouble(), any());
        verify(telegramNotifier, times(2)).send(any());

        AlertStatus status = monitor.status();
        assertEquals(AlertState.OK, status.state());
        assertEquals("RECOVERY", status.lastAlertType());
    }

    @Test
    void yieldEqualToThresholdIsOkBoundaryNotBreach() {
        when(metricsQuery.recentWindowYield(200))
                .thenReturn(Optional.of(new WindowYield(200, 190))); // exactly 95.0%

        assertEquals(AlertState.OK, monitor.evaluateNow());

        verify(telegramNotifier, never()).send(any());
        assertEquals(AlertState.OK, monitor.status().state());
        assertNull(monitor.status().lastAlertAt());
    }
@Test
    void insufficientSampleDoesNotAlertAndReportsInsufficientData() {
        when(metricsQuery.recentWindowYield(200))
                .thenReturn(Optional.of(new WindowYield(10, 8))); // 10 < 20 min

        assertEquals(AlertState.INSUFFICIENT_DATA, monitor.evaluateNow());

        verify(telegramNotifier, never()).send(any());
        AlertStatus status = monitor.status();
        assertEquals(AlertState.INSUFFICIENT_DATA, status.state());
        assertEquals(10, status.lastSampleUnits());
        assertEquals(80.0, status.lastYieldPercent(), 0.0001);
    }

    @Test
    void insufficientSamplePreservesAlertStateUntilRecovery() {
        when(metricsQuery.recentWindowYield(200))
                .thenReturn(Optional.of(new WindowYield(200, 180)))  // 90.0% → ALERT
                .thenReturn(Optional.of(new WindowYield(5, 5)))      // gap: too small
                .thenReturn(Optional.of(new WindowYield(200, 195))); // 97.5% → recovery

        assertEquals(AlertState.ALERT, monitor.evaluateNow());
        assertEquals(AlertState.INSUFFICIENT_DATA, monitor.evaluateNow());
        assertEquals(AlertState.OK, monitor.evaluateNow());

        // State survived the gap: recovery fired instead of a second breach
        verify(messageBuilder, times(1)).breach(any(), anyInt(), anyDouble(), any());
        verify(messageBuilder, times(1)).recovery(any(), anyInt(), anyDouble(), any());
        verify(telegramNotifier, times(2)).send(any());
        assertEquals("RECOVERY", monitor.status().lastAlertType());
    }

    @Test
    void disabledNotifierStillEvaluatesAndTransitions() {
        when(telegramNotifier.isEnabled()).thenReturn(false);
        when(telegramNotifier.send(any())).thenReturn(TelegramSendResult.DISABLED);
        when(metricsQuery.recentWindowYield(200))
                .thenReturn(Optional.of(new WindowYield(200, 170))); // 85.0%

        assertEquals(AlertState.ALERT, monitor.evaluateNow());

        verify(telegramNotifier, times(1)).send(any());
        AlertStatus status = monitor.status();
        assertEquals(AlertState.ALERT, status.state());
        assertFalse(status.telegramEnabled());
        assertNull(status.lastAlertAt()); // nothing was actually delivered
    }
}