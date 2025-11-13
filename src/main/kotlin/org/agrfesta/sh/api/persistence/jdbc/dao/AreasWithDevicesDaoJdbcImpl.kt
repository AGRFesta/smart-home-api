package org.agrfesta.sh.api.persistence.jdbc.dao

import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasWithDevicesJdbcRepository
import org.springframework.stereotype.Service

@Service
class AreasWithDevicesDaoJdbcImpl(
    private val areasWithDevicesJdbcRepo: AreasWithDevicesJdbcRepository
): AreasWithDevicesDao {

    override fun getAllAreasWithDevices(): Collection<AreaDtoWithDevices> {
        return areasWithDevicesJdbcRepo.getAll()
    }

}
