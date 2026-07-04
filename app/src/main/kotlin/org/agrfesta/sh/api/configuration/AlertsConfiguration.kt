package org.agrfesta.sh.api.configuration

import org.agrfesta.sh.api.core.domain.alerts.BatteryLowRule
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
}
