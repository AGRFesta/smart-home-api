package org.agrfesta.sh.api.domain.devices

interface DevicesProvider {
    fun getAllDevices(): Collection<DeviceDataValue>
}
