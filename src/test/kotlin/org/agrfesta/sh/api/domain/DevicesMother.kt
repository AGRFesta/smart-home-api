package org.agrfesta.sh.api.domain

import java.util.*

fun aDevice(
    providerId: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    status: DeviceStatus = DeviceStatus.PAIRED,
    name: String = UUID.randomUUID().toString()
) = Device(providerId, provider, name, status)
