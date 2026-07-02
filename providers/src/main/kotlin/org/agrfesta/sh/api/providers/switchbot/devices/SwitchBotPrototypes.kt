package org.agrfesta.sh.api.providers.switchbot.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType
import org.springframework.stereotype.Component

/**
 * SwitchBot device prototypes. Registered as **unconditional** `@Component`s (not
 * `@ConditionalOnSwitchBot`): the jar is always on the classpath, only credentials are conditional,
 * so the read path stays alive when the provider is disabled. The model strings reuse
 * [SwitchBotDeviceType] as the single source of truth, avoiding drift with the provider mapping.
 */

@Component
class SwitchBotMeterPrototype : DevicePrototype {
    override val model = DeviceModel(SwitchBotDeviceType.METER.model)
    override val provider = Provider.SWITCHBOT
    override val driverType = SwitchBotMeter::class
    override val roles = setOf(SENSOR)
}

@Component
class SwitchBotMeterPlusPrototype : DevicePrototype {
    override val model = DeviceModel(SwitchBotDeviceType.METER_PLUS.model)
    override val provider = Provider.SWITCHBOT
    override val driverType = SwitchBotMeter::class
    override val roles = setOf(SENSOR)
}

@Component
class SwitchBotWoIOSensorPrototype : DevicePrototype {
    override val model = DeviceModel(SwitchBotDeviceType.WO_IO_SENSOR.model)
    override val provider = Provider.SWITCHBOT
    override val driverType = SwitchBotMeter::class
    override val roles = setOf(SENSOR)
}

@Component
class SwitchBotHubPrototype : DevicePrototype {
    override val model = DeviceModel(SwitchBotDeviceType.HUB_MINI.model)
    override val provider = Provider.SWITCHBOT
    override val driverType = SwitchBotMiniHub::class
    override val roles = emptySet<DeviceFeature>()
}
