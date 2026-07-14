package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceViewRepository
import org.agrfesta.sh.api.core.application.readmodels.devices.DeviceView
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import org.agrfesta.sh.api.persistence.jdbc.repositories.DeviceViewJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeviceViewRepositoryJdbcImpl(
    private val deviceViewRepo: DeviceViewJdbcRepository
) : DeviceViewRepository {

    private val logger by LoggerDelegate()

    override fun findById(deviceId: UUID): Either<GetDeviceFailure, DeviceView> = try {
        deviceViewRepo.findViewById(deviceId)?.right()
            ?: DeviceNotFound(deviceId).left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching device view '$deviceId'", e)
        DeviceRepositoryError.left()
    }
}
