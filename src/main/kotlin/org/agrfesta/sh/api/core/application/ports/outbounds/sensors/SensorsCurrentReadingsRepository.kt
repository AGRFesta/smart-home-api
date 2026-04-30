package org.agrfesta.sh.api.core.application.ports.outbounds.sensors

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupFailure

/**
 * Outbound port for retrieving the most recent cached sensor readings.
 */
interface SensorsCurrentReadingsRepository {

    /**
     * Returns the latest cached [ThermoHygroData] for the given [sensor], or `null` if no
     * reading has been stored yet.
     *
     * @param sensor the provider identity of the sensor to look up.
     * @return [Either.Right] with the [ThermoHygroData] (or `null` when absent),
     * or [Either.Left] with [ReadingsLookupFailure] if the lookup itself fails.
     */
    fun findBy(sensor: DeviceProviderIdentity): Either<ReadingsLookupFailure, ThermoHygroData?>

}
