package com.inspectiq.ingestion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects write (POST) endpoints with a shared secret sent in the
 * X-Device-Api-Key header. Read (GET) endpoints stay open for the dashboard
 * and analytics-service in this phase.
 *
 * Why not Spring Security? A single header check is all that is required right
 * now (KISS/YAGNI); pulling in the full security starter would add filters,
 * session machinery and config surface we do not use.
 */
@Component
public class DeviceApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DeviceApiKeyFilter.class);

    private static final String API_KEY_HEADER = "X-Device-Api-Key";

    private final String configuredApiKey;

    public DeviceApiKeyFilter(@Value("${inspectiq.device-api-key:}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey == null ? "" : configuredApiKey.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only guard write endpoints; GETs (dashboard/analytics reads) stay open.
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (configuredApiKey.isEmpty()) {
            // Fail open with a loud warning so local dev works out of the box,
            // but the operator knows the service is unprotected.
            log.warn("DEVICE_API_KEY is not set — POST endpoints are UNPROTECTED. "
                    + "Set it in .env before exposing this service beyond localhost.");
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !keysMatch(providedKey, configuredApiKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"title\":\"Unauthorized\",\"status\":401,"
                            + "\"detail\":\"Missing or invalid X-Device-Api-Key header\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Constant-time comparison so response timing cannot leak how many
     * characters of the key an attacker guessed correctly.
     */
    private boolean keysMatch(String provided, String configured) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                configured.getBytes(StandardCharsets.UTF_8));
    }
}