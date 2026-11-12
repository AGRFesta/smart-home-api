package org.agrfesta.sh.api.core.domain.heating

import java.util.Locale

/**
 * Enumeration of available strategies for controlling shared heating areas.
 *
 * These strategies define how the system decides to turn a shared heater ON or OFF
 * based on the requirements of the associated areas.
 */
enum class SharedHeatingStrategy {
    ECONOMY,
    COMFORT;

    companion object {
        /**
         * Returns the strategy named by [name], ignoring case and surrounding spaces, or `null` when
         * [name] names no strategy.
         */
        fun fromName(name: String): SharedHeatingStrategy? {
            val normalized = name.trim().uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == normalized }
        }
    }
}
