package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetDeviceService(
    private val deviceAggregateRepository: DeviceAggregateRepository,
    private val deviceBatteryRepository: DeviceBatteryRepository
) : GetDeviceUseCase {

    private val logger by LoggerDelegate()

    override fun execute(deviceId: UUID): Either<GetDeviceFailure, DeviceAggregate> =
        deviceAggregateRepository.findById(deviceId)
            .map { aggregate -> aggregate.copy(batteryLevel = resolveBatteryLevel(aggregate)) }

    /**
     * Best-effort lookup of the cached battery level: a lookup failure degrades to `null` (battery is
     * not essential to the device read) but is logged so a Redis outage or cache corruption is not
     * silently invisible.
     */
    private fun resolveBatteryLevel(aggregate: DeviceAggregate): Int? =
        deviceBatteryRepository.findBy(aggregate)
            .onLeft { failure -> logger.error("Failed to read cached battery for device ${aggregate.uuid}: $failure") }
            .getOrNull()
}
