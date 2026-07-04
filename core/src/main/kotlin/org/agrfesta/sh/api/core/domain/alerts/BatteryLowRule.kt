package org.agrfesta.sh.api.core.domain.alerts

/**
 * The `BATTERY_LOW` rule: a two-threshold hysteresis band over a numeric battery level (0–100).
 *
 * @param trigger the level at or below which the condition becomes true when no alert is open.
 * @param clear the level at or above which the condition clears for an already-open alert.
 */
class BatteryLowRule(
    private val trigger: Int,
    private val clear: Int
) {
    init {
        require(clear > trigger) { "clear threshold ($clear) must be strictly greater than trigger ($trigger)" }
    }

    /**
     * Whether the `BATTERY_LOW` condition is currently true for the given [level], applying hysteresis
     * through [alertOpen] (the stickiness of an already-open alert).
     */
    fun conditionMet(level: Int, alertOpen: Boolean): Boolean = level <= trigger || (alertOpen && level < clear)
}
