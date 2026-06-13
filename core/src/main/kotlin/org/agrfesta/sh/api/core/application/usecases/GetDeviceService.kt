package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetDeviceService(
    private val deviceAggregateRepository: DeviceAggregateRepository
) : GetDeviceUseCase {

    override fun execute(deviceId: UUID): Either<GetDeviceFailure, DeviceAggregate> =
        deviceAggregateRepository.findById(deviceId)
}
