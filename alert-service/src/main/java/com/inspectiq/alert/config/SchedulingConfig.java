package com.inspectiq.alert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled so YieldAlertMonitor's periodic tick runs in the alert-service.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}