package org.agrfesta.sh.api.core.domain.heating

/**
 * Enumeration of available strategies for controlling shared heating areas.
 *
 * These strategies define how the system decides to turn a shared heater ON or OFF
 * based on the requirements of the associated areas.
 */
enum class SharedHeatingStrategy {
    ECONOMY,
    COMFORT
}
