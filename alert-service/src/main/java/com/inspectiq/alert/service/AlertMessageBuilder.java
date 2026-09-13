package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.AlertMessage;
import com.inspectiq.alert.dto.AlertDtos.WindowYield;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pure HTML formatters for Telegram notifications. No I/O and no Spring state —
 * the exact rendered text is therefore unit-testable with literal assertions.
 * Messages are Telegram-HTML (`<b>` plus emoji), as sent with parse_mode "HTML".
 */
@Component
public class AlertMessageBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx");

    /** Breach notification: rolling yield dropped below the threshold. */
    public AlertMessage breach(WindowYield yield, int lookback, double thresholdPercent, OffsetDateTime checkedAt) {
        String text = String.format(Locale.ROOT,
                "<b>\u26A0\uFE0F YIELD ALERT \u2014 InspectIQ</b>\n" +
                        "Rolling yield over the last %d inspections dropped to " +
                        "<b>%.1f%%</b> (threshold %.1f%%).\n" +
                        "Sample: %d units inspected, %d passed.\n" +
                        "Checked at: %s",
                lookback, yieldPercent(yield), thresholdPercent,
                yield.inspectedUnits(), yield.passCount(), formatTimestamp(checkedAt));
        return new AlertMessage(text, true);
    }

    /** Recovery notification: yield returned to (or above) the threshold. */
    public AlertMessage recovery(WindowYield yield, int lookback, double thresholdPercent, OffsetDateTime checkedAt) {
        String text = String.format(Locale.ROOT,
                "<b>\u2705 YIELD RECOVERED \u2014 InspectIQ</b>\n" +
                        "Rolling yield over the last %d inspections recovered to " +
                        "<b>%.1f%%</b> (threshold %.1f%%).\n" +
                        "Sample: %d units inspected, %d passed.\n" +
                        "Checked at: %s",
                lookback, yieldPercent(yield), thresholdPercent,
                yield.inspectedUnits(), yield.passCount(), formatTimestamp(checkedAt));
        return new AlertMessage(text, false);
    }

    private static double yieldPercent(WindowYield yield) {
        return (yield.passCount() * 100.0) / yield.inspectedUnits();
    }

    private static String formatTimestamp(OffsetDateTime checkedAt) {
        return TIMESTAMP_FORMAT.format(checkedAt);
    }
}