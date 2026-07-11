package org.agrfesta.sh.api.configuration

import org.agrfesta.sh.api.core.domain.alerts.BatteryLowRule
import org.agrfesta.sh.api.core.domain.notifications.ReminderPolicy
import org.agrfesta.sh.api.core.domain.notifications.RetentionPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AlertsConfiguration {

    /**
     * Global `BATTERY_LOW` thresholds (per-device overrides are a future issue). The rule itself
     * rejects a band where `clear <= trigger` at startup.
     */
    @Bean
    fun batteryLowRule(
        @Value("\${alerts.battery-low.trigger:15}") trigger: Int,
        @Value("\${alerts.battery-low.clear:25}") clear: Int
    ) = BatteryLowRule(trigger = trigger, clear = clear)

    /**
     * Global reminder cadence (#194), ISO-8601 duration, default daily. Per-device cadence is a
     * future issue.
     */
    @Bean
    fun reminderPolicy(
        @Value("\${alerts.notifications.reminder-interval:PT24H}") reminderInterval: Duration
    ) = ReminderPolicy(cadence = reminderInterval)

    /** Retention window for persisted notifications (#194), ISO-8601 duration, default 90 days. */
    @Bean
    fun retentionPolicy(
        @Value("\${alerts.notifications.retention:P90D}") retention: Duration
    ) = RetentionPolicy(window = retention)
}
