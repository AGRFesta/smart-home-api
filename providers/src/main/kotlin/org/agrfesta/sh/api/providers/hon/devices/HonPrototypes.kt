package org.agrfesta.sh.api.providers.hon.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.hon.HonService
import org.springframework.stereotype.Component

/**
 * hOn device prototypes. Registered as **unconditional** `@Component`s (not
 * `@ConditionalOnHon`): the jar is always on the classpath, only credentials are conditional,
 * so the read path stays alive when the provider is disabled. The model strings reuse
 * [HonService]'s constants as the single source of truth, avoiding drift with the provider
 * mapping.
 *
 * Roles are empty until [HonAc] implements the capability interfaces (SENSOR arrives with the
 * readings issue, ACTUATOR with the commands issue — #250/#251): roles are user-visible
 * behavior (feature filters, area assignments) and must match what the driver can actually do,
 * as pinned by `DevicePrototypesContractTest` (precedent: `SwitchBotHubPrototype`).
 */

@Component
class HonAcAs25Prototype : DevicePrototype {
    override val model = DeviceModel(HonService.AC_AS25PBPHRA_PRE_MODEL)
    override val provider = Provider.HON
    override val driverType = HonAc::class
    override val roles = emptySet<DeviceFeature>()
}

@Component
class HonAcAs35Prototype : DevicePrototype {
    override val model = DeviceModel(HonService.AC_AS35PBPHRA_PRE_MODEL)
    override val provider = Provider.HON
    override val driverType = HonAc::class
    override val roles = emptySet<DeviceFeature>()
}
