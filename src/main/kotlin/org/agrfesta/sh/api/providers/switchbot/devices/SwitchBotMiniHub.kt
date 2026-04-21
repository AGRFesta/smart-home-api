package org.agrfesta.sh.api.providers.switchbot.devices

import java.util.*
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider

class SwitchBotMiniHub(
    override val uuid: UUID,
    override val deviceProviderId: String
) : Device {
    override val provider: Provider = Provider.NETATMO



}
