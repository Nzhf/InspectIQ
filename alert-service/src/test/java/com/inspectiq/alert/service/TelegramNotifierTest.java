package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.AlertMessage;
import com.inspectiq.alert.dto.AlertDtos.TelegramSendResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline integration test of the Telegram HTTP path: MockWebServer stands in
 * for api.telegram.org so the bot is never contacted and the behaviour is
 * deterministic on any machine.
 */
class TelegramNotifierTest {

    private static final String TOKEN = "123456:ABC-test-token";
    private static final String CHAT_ID = "-1001234567890";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private TelegramNotifier notifier(String token, String chatId) {
        String baseUrl = server.url("/").toString().replaceFirst("/$", "");
        return new TelegramNotifier(token, chatId, baseUrl);
    }

    @Test
    void sendPostsJsonToBotSendMessageEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true,\"result\":{\"message_id\":42}}"));

        TelegramSendResult result = notifier(TOKEN, CHAT_ID)
                .send(new AlertMessage("<b>YIELD ALERT</b>\nline two", true));

        RecordedRequest request = server.takeRequest();
        assertEquals(TelegramSendResult.SENT, result);
        assertEquals("POST", request.getMethod());
        assertEquals("/bot" + TOKEN + "/sendMessage", request.getPath());
        assertEquals("application/json", request.getHeader("Content-Type"));

        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"chat_id\":\"" + CHAT_ID + "\""), body);
        assertTrue(body.contains("\"parse_mode\":\"HTML\""), body);
        assertTrue(body.contains("\"text\":\"<b>YIELD ALERT</b>\\nline two\""), body);
    }

    @Test
    void okFalseInBodyReturnsFailedEvenWith200Status() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request\"}"));

        TelegramSendResult result = notifier(TOKEN, CHAT_ID)
                .send(new AlertMessage("text", true));

        assertEquals(TelegramSendResult.FAILED, result);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void http500ReturnsFailed() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("boom"));

        TelegramSendResult result = notifier(TOKEN, CHAT_ID)
                .send(new AlertMessage("text", true));

        assertEquals(TelegramSendResult.FAILED, result);
    }

    @Test
    void blankTokenDisablesWithoutAnyRequest() {
        TelegramSendResult result = notifier("", CHAT_ID)
                .send(new AlertMessage("text", true));

        assertEquals(TelegramSendResult.DISABLED, result);
        assertEquals(0, server.getRequestCount());
    }
}