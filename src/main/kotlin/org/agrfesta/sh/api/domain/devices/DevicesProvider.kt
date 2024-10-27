package org.agrfesta.sh.api.domain.devices

interface DevicesProvider {
    val provider: Provider
    fun getAllDevices(): Collection<DeviceDataValue> //TODO introduce monads
}
