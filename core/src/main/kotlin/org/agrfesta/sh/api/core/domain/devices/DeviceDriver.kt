package org.agrfesta.sh.api.core.domain.devices

import java.util.*

interface DeviceDriver: DeviceProviderIdentity {
    val uuid: UUID
}
