package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure

/**
 * Capability of a [DeviceDriver] that can return the provider's realtime, unfiltered truth about
 * itself as a JSON [String].
 *
 * Diagnostics is a distinct device capability (ISP) from reading sensor values or driving actuators:
 * a driver opts in by implementing this interface. Returning [String] keeps the core free of any JSON
 * library.
 *
 * How faithful the body is depends on the driver:
 * - **Pass-through drivers** (e.g. SwitchBot) return the provider body **verbatim** — no parse →
 *   re-serialize round trip, so keys and fields are exactly as the provider sent them.
 * - **Scoping drivers** (e.g. Netatmo, which extracts the device's own room from the home status)
 *   necessarily parse and re-serialize a subtree; all fields are preserved, but key order may differ
 *   from the original provider payload.
 */
interface Inspectable {

    /**
     * Inspects this device and returns the provider's raw response body (or a device-scoped subtree
     * of it, depending on the driver).
     *
     * @return [Either.Right] with the JSON body,
     * or [Either.Left] with a [DevicesProviderFailure] when the provider is unreachable or errors.
     */
    fun inspect(): Either<DevicesProviderFailure, String>
}
