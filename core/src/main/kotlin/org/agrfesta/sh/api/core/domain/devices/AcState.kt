package org.agrfesta.sh.api.core.domain.devices

import org.agrfesta.sh.api.core.domain.commons.Temperature

/**
 * Control state of an air conditioner as reported by its driver. Every field except [power]
 * is nullable: the device context may not report a parameter, or report a code the domain
 * does not map — the driver must surface "unknown", never guess.
 */
data class AcState(
    val power: ActuatorStatus,
    val mode: AcMode?,
    val targetTemperature: Temperature?,
    val fanSpeed: AcFanSpeed?
)
