package org.agrfesta.sh.api.services.heating

import java.math.BigDecimal
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.SharedHeaterContext
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.utils.LoggerDelegate

/**
 * Strategy interface for controlling a shared heater across multiple heatable areas.
 *
 * Implementations define specific logic to determine when the shared heater should be turned ON or OFF
 * based on the state and requirements of the associated areas.
 */
sealed interface SharedHeatingAreasStrategyService {
    suspend fun handleHeatingFor(context: SharedHeaterContext)
}

/**
 * Base implementation of [SharedHeatingAreasStrategyService] providing common validation and utility methods.
 *
 * This class handles preliminary checks (e.g., ensuring areas exist and share the same heater)
 * before delegating the specific heating logic to [internalHandleHeatingFor].
 *
 * It also provides helper methods to determine if a specific area requires heating based on
 * current temperature, target temperature, and hysteresis logic.
 */
//abstract class AbstractSharedHeatingAreasStrategyService(
//    protected val hysteresis: BigDecimal,
//): SharedHeatingAreasStrategyService {
//    private val logger by LoggerDelegate()
//
//
//
//}
