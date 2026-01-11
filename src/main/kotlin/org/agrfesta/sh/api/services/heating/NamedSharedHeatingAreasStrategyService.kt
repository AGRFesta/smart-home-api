package org.agrfesta.sh.api.services.heating

/**
 * Interface for heating strategy services that are identified by a specific [SharedHeatingAreasStrategy] type.
 *
 * This allows multiple implementations to coexist and be selected dynamically based on the
 * configured strategy enum.
 *
 * @property strategy The specific strategy type implemented by this service.
 */
interface NamedSharedHeatingAreasStrategyService: SharedHeatingAreasStrategyService {
    val strategy: SharedHeatingAreasStrategy
}
