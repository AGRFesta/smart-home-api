package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import org.agrfesta.sh.api.persistence.jdbc.repositories.DeviceAggregateJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeviceAggregateRepositoryJdbcImpl(
    private val deviceAggregateRepo: DeviceAggregateJdbcRepository
) : DeviceAggregateRepository {

    private val logger by LoggerDelegate()

    override fun findById(deviceId: UUID): Either<GetDeviceFailure, DeviceAggregate> = try {
        deviceAggregateRepo.findAggregateById(deviceId)?.right()
            ?: DeviceNotFound(deviceId).left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error fetching device aggregate '$deviceId'", e)
        DeviceRepositoryError.left()
    }
}
