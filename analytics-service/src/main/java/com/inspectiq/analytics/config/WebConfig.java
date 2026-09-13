package com.inspectiq.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is configured explicitly from Day 1 (never left to browser defaults or
 * wildcard assumptions). Origins come from the CORS_ALLOWED_ORIGINS env var so
 * each deployment (local, staging, prod) can allowlist its own dashboard URL.
 */
@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${inspectiq.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {
        String[] origins = allowedOrigins.split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}