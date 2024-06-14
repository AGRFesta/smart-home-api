package org.agrfesta.sh.api.persistence

import org.agrfesta.sh.api.domain.Device
import org.springframework.stereotype.Service

interface DevicesDao {
    fun getAll(): Collection<Device>
}

@Service
class DevicesDaoImpl: DevicesDao {
    override fun getAll(): Collection<Device> {
        return emptyList()
    }
}
