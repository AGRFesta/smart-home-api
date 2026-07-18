package org.agrfesta.sh.api.providers.hon.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Provider
import java.util.UUID

/** No-op driver for hOn appliances whose model has no prototype yet (non-AC types). */
class HonUnknownDevice(
    override val uuid: UUID,
    override val deviceProviderId: String
) : DeviceDriver {
    override val provider: Provider = Provider.HON
}
