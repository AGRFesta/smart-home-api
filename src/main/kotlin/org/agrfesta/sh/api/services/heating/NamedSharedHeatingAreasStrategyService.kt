package org.agrfesta.sh.api.services.heating

import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy

/**
 * Interface for heating strategy services that are identified by a specific [SharedHeatingStrategy] type.
 *
 * This allows multiple implementations to coexist and be selected dynamically based on the
 * configured strategy enum.
 *
 * @property strategy The specific strategy type implemented by this service.
 */
interface NamedSharedHeatingAreasStrategyService: SharedHeatingAreasStrategyService {
    val strategy: SharedHeatingStrategy
}
