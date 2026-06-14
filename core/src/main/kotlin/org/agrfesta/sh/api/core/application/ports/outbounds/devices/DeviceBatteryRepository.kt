package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.failures.BatteryLookupFailure
import org.agrfesta.sh.api.core.domain.failures.BatterySaveFailure

/**
 * Outbound port for storing and retrieving the most recent cached battery level of a device.
 *
 * Battery is a device-level concern (not a sensor one): it is namespaced separately from sensor
 * readings and is collected for any [org.agrfesta.sh.api.core.domain.devices.BatteryPowered] driver.
 */
interface DeviceBatteryRepository {

    /**
     * Returns the latest cached battery level (0–100) for the given [device], or `null` if no value
     * has been stored yet (or it has expired).
     *
     * @param device the provider identity of the device to look up.
     * @return [Either.Right] with the battery percentage (or `null` when absent),
     * or [Either.Left] with [BatteryLookupFailure] if the lookup itself fails.
     */
    fun findBy(device: DeviceProviderIdentity): Either<BatteryLookupFailure, Int?>

    /**
     * Stores the [batteryLevel] as the current value for the given [device], overwriting any
     * previously cached value, subject to the configured TTL.
     *
     * @param device the provider identity of the device whose battery is being stored.
     * @param batteryLevel the battery percentage (0–100) to cache.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with [BatterySaveFailure] if
     * the write fails.
     */
    fun save(device: DeviceProviderIdentity, batteryLevel: Int): Either<BatterySaveFailure, Unit>
}
