package org.agrfesta.sh.api.providers.switchbot.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Provider
import java.util.*

class SwitchBotMiniHub(
    override val uuid: UUID,
    override val deviceProviderId: String
) : DeviceDriver {
    override val provider: Provider = Provider.SWITCHBOT
}
