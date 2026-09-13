package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.AlertMessage;
import com.inspectiq.alert.dto.AlertDtos.WindowYield;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertMessageBuilderTest {

    private final AlertMessageBuilder builder = new AlertMessageBuilder();

    @Test
    void breachRendersThresholdsSampleAndTimestampAsHtml() {
        OffsetDateTime checkedAt = OffsetDateTime.of(2026, 9, 13, 10, 30, 0, 0, ZoneOffset.UTC);

        AlertMessage message = builder.breach(new WindowYield(200, 183), 200, 95.0, checkedAt);

        assertTrue(message.isBreach());
        String text = message.text();
        assertTrue(text.contains("<b>"), text);
        assertTrue(text.contains("YIELD ALERT"), text);
        assertTrue(text.contains("91.5%"), text);   // 183 / 200
        assertTrue(text.contains("95.0%"), text);   // threshold
        assertTrue(text.contains("200"), text);     // window size and sample size
        assertTrue(text.contains("183 passed"), text);
        assertTrue(text.contains("2026-09-13 10:30:00 +00:00"), text);
    }

    @Test
    void recoveryRendersAsNonBreachMessage() {
        AlertMessage message = builder.recovery(new WindowYield(200, 192), 200, 95.0, OffsetDateTime.now());

        assertFalse(message.isBreach());
        String text = message.text();
        assertTrue(text.contains("RECOVERED"), text);
        assertTrue(text.contains("96.0%"), text);   // 192 / 200
        assertTrue(text.contains("95.0%"), text);   // threshold
    }
}