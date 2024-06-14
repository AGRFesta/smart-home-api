package org.agrfesta.sh.api.domain

import java.util.*

fun aProviderDevice(
    id: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = UUID.randomUUID().toString()
) = ProviderDevice(id, provider, name)

fun aDevice(
    uuid: UUID = UUID.randomUUID(),
    providerId: String = UUID.randomUUID().toString(),
    provider: Provider = Provider.SWITCHBOT,
    name: String = UUID.randomUUID().toString()
) = Device(uuid, providerId, provider, name)
