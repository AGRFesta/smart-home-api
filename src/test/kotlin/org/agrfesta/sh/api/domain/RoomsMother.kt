package org.agrfesta.sh.api.domain

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.*

fun aRoom(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    devices: Collection<Device> = emptyList()
) = Room(uuid, name, devices)
