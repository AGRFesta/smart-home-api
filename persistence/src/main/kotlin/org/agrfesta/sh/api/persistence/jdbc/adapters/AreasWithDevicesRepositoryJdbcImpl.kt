package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasWithDevicesJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class AreasWithDevicesRepositoryJdbcImpl(
    private val areasWithDevicesJdbcRepo: AreasWithDevicesJdbcRepository
): AreasWithDevicesRepository {

    private val logger by LoggerDelegate()

    override fun getAllAreasWithDevices(): Either<AreaRepositoryError, Collection<AreaDtoWithDevices>> = try {
        areasWithDevicesJdbcRepo.getAll().right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasWithDevicesRepositoryJdbcImpl", e)
        AreaRepositoryError.left()
    }

}
