package org.agrfesta.sh.api.core.domain.devices

import java.util.*

interface Device: DeviceProviderIdentity {
    val uuid: UUID
}
