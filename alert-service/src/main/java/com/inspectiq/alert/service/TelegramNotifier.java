package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.AlertMessage;
import com.inspectiq.alert.dto.AlertDtos.TelegramSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Thin Telegram Bot API client built on Spring's {@link RestClient} — no
 * heavyweight telegram library needed.
 *
 * Every failure mode is handled gracefully (no exception propagation): a missing
 * bot token / chat id means nothing is sent (monitoring continues), and any
 * network error, non-2xx status or {@code ok:false} body is reported as FAILED
 * after logging. This keeps the scheduled monitor isolated from Telegram outages.
 */
@Component
public class TelegramNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramNotifier.class);

    private final String botToken;
    private final String chatId;
    private final String apiBaseUrl;
    private final RestClient restClient;

    public TelegramNotifier(
            @Value("${inspectiq.telegram.bot-token:}") String botToken,
            @Value("${inspectiq.telegram.chat-id:}") String chatId,
            @Value("${inspectiq.telegram.api-base-url:https://api.telegram.org}") String apiBaseUrl) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.apiBaseUrl = apiBaseUrl;
        this.restClient = RestClient.create();
    }

    /** True only when both the bot token and the target chat are configured. */
    public boolean isEnabled() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    /**
     * Sends a message via POST {@code {base}/bot{token}/sendMessage}.
     *
     * @return SENT on a successful Telegram 200 + {@code ok:true}, DISABLED when
     *         not configured, or FAILED for any transport/API error (no throw)
     */
    public TelegramSendResult send(AlertMessage message) {
        if (!isEnabled()) {
            LOGGER.warn("Telegram not configured (missing bot token or chat id) — skipping send");
            return TelegramSendResult.DISABLED;
        }

        String url = apiBaseUrl + "/bot" + botToken + "/sendMessage";
        try {
            RestClient.ResponseSpec response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", message.text(),
                            "parse_mode", "HTML"))
                    .retrieve();

            // RestClient throws RestClientResponseException for any status >= 400,
            // so reaching the body means Telegram returned 2xx. The success flag
            // still lives in the body ({"ok":true,...}) and must be checked.
            Object body = response.body(Map.class);
            if (body instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("ok"))) {
                return TelegramSendResult.SENT;
            }
            LOGGER.warn("Telegram sendMessage returned ok != true");
            return TelegramSendResult.FAILED;
        } catch (RestClientResponseException ex) {
            LOGGER.warn("Telegram sendMessage failed with HTTP {}: {}",
                    ex.getStatusCode(), ex.getMessage());
            return TelegramSendResult.FAILED;
        } catch (RestClientException ex) {
            LOGGER.warn("Telegram sendMessage call failed: {}", ex.getMessage());
            return TelegramSendResult.FAILED;
        }
    }
}