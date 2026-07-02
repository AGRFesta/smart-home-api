package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import kotlin.reflect.KClass

/**
 * Client-free descriptor of a device model: the single source of truth that maps a persisted
 * [model] to the driver that talks to it ([driverType]) and to the [roles] it can play in an area.
 *
 * Implemented as unconditional beans in `:providers` (one per model) so the read path stays alive
 * even when a provider is disabled — only the provider's credentials are conditional, not the jar.
 */
interface DevicePrototype {
    val model: DeviceModel
    val provider: Provider
    val driverType: KClass<out DeviceDriver>
    val roles: Set<DeviceFeature>
}
