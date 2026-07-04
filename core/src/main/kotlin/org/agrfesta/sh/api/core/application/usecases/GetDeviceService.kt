package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceAggregateRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.devices.DeviceAggregate
import org.agrfesta.sh.api.core.domain.failures.GetDeviceFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetDeviceService(
    private val deviceAggregateRepository: DeviceAggregateRepository,
    private val deviceBatteryRepository: DeviceBatteryRepository,
    private val alertsRepository: AlertsRepository
) : GetDeviceUseCase {

    private val logger by LoggerDelegate()

    override fun execute(deviceId: UUID): Either<GetDeviceFailure, DeviceAggregate> =
        deviceAggregateRepository.findById(deviceId)
            .map { aggregate ->
                aggregate.copy(
                    batteryLevel = resolveBatteryLevel(aggregate),
                    activeAlerts = resolveActiveAlerts(aggregate.uuid)
                )
            }

    /**
     * Best-effort lookup of the open alerts targeting the device: a lookup failure degrades to `null`
     * ("unknown", never a false "no alerts") but is logged so an alert store outage is not silently
     * invisible.
     */
    private fun resolveActiveAlerts(deviceId: UUID): Set<AlertType>? =
        alertsRepository.getAlerts(AlertStatus.OPEN)
            .onLeft { failure -> logger.error("Failed to read open alerts for device $deviceId: $failure") }
            .getOrNull()
            ?.filter { it.target == AlertTarget.Device(deviceId) }
            ?.map { it.type }
            ?.toSet()

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
