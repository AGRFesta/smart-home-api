package org.agrfesta.sh.api.core.domain.devices

import org.agrfesta.sh.api.core.domain.commons.Temperature

/**
 * On/off command of an air conditioner. Deliberately distinct from [ActuatorStatus]: a
 * *reading* can be [ActuatorStatus.UNDEFINED] ("the device did not report"), a command
 * cannot — the type makes that state unrepresentable.
 */
enum class AcPowerCommand { ON, OFF }

/**
 * Partial update of an air conditioner's settings: only non-null fields are commands, the
 * device keeps its current value for everything else (the driver's read-modify-write fills
 * the rest).
 */
data class AcSettingsUpdate(
    val power: AcPowerCommand? = null,
    val mode: AcMode? = null,
    val targetTemperature: Temperature? = null,
    val fanSpeed: AcFanSpeed? = null
)
