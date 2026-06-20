package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure

/**
 * Capability of a [DeviceDriver] that is battery-powered and can report its current battery level.
 *
 * Being battery-powered is orthogonal to being a [Sensor] or an [Actuator] (ISP): a driver opts in by
 * implementing this interface, independently of any other capability. The level is a numeric percentage
 * (0–100), following SwitchBot semantics.
 */
interface BatteryPowered {

    /**
     * Reads the current battery level of this device.
     *
     * @return [Either.Right] with the battery percentage (0–100),
     * or [Either.Left] with a [DevicesProviderFailure] when the provider is unreachable or errors.
     */
    fun batteryLevel(): Either<DevicesProviderFailure, Int>
}
