package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasWithDevicesRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasWithDevicesJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class AreasWithDevicesRepositoryJdbcImpl(
    private val areasWithDevicesJdbcRepo: AreasWithDevicesJdbcRepository
): AreasWithDevicesRepository {

    override fun getAllAreasWithDevices(): Either<PersistenceFailure, Collection<AreaDtoWithDevices>> = try {
        areasWithDevicesJdbcRepo.getAll().right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
