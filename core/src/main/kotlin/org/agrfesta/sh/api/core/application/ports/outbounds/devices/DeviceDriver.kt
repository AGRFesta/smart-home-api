package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import java.util.*

interface DeviceDriver : DeviceProviderIdentity {
    val uuid: UUID
}
