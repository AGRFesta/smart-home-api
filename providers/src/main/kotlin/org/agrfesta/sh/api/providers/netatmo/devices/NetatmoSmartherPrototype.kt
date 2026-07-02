package org.agrfesta.sh.api.providers.netatmo.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.netatmo.NetatmoService
import org.springframework.stereotype.Component

/**
 * Netatmo Smarther prototype. Registered as an **unconditional** `@Component` (not
 * `@ConditionalOnNetatmo`) so the read path survives when the provider is disabled. The model string
 * reuses [NetatmoService.SMARTHER_MODEL] as the single source of truth to avoid drift with the
 * provider mapping.
 */
@Component
class NetatmoSmartherPrototype : DevicePrototype {
    override val model = DeviceModel(NetatmoService.SMARTHER_MODEL)
    override val provider = Provider.NETATMO
    override val driverType = NetatmoSmarther::class
    override val roles = setOf(SENSOR, ACTUATOR)
}
