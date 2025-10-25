package org.agrfesta.sh.api.domain.devices

import java.util.*

interface Device: DeviceProviderIdentity {
    val uuid: UUID
}
