package org.agrfesta.sh.api.core.application.ports.outbounds.sensors

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupFailure
import org.agrfesta.sh.api.core.domain.failures.SensorReadingsSaveFailure

/**
 * Outbound port for storing and retrieving the most recent cached sensor readings.
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

    /**
     * Stores the [data] as the current reading for the given [sensor], overwriting any
     * previously cached value.
     *
     * @param sensor the provider identity of the sensor whose reading is being stored.
     * @param data the thermo-hygrometric data to cache.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with
     * [SensorReadingsSaveFailure] if the write fails.
     */
    fun save(sensor: DeviceProviderIdentity, data: ThermoHygroData): Either<SensorReadingsSaveFailure, Unit>
}
