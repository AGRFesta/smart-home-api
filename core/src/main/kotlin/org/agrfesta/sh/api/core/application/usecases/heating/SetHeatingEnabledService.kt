package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import arrow.core.raise.either
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.SetHeatingEnabledUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Heater
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.domain.failures.SetHeatingEnabledFailure
import org.agrfesta.sh.api.core.domain.heating.SetHeatingEnabledReport
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SetHeatingEnabledService(
    private val propertyRepository: PropertyRepository,
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher,
    private val registeredDeviceDrivers: RegisteredDeviceDrivers,
    private val heatingActuationLock: HeatingActuationLock
) : SetHeatingEnabledUseCase {

    private val logger by LoggerDelegate()

    override fun execute(enabled: Boolean): Either<SetHeatingEnabledFailure, SetHeatingEnabledReport> =
        heatingActuationLock.withLock {
            either {
                propertyRepository.upsert(HEATING_ENABLED_KEY, enabled.toString())
                    .onRight { homeStateRefreshPublisher.publish() }
                    .bind()
                val failedHeaters = if (enabled) {
                    emptySet()
                } else {
                    turnOff(registeredDeviceDrivers.load().bind().values.filterIsInstance<Heater>())
                }
                SetHeatingEnabledReport(failedHeaters = failedHeaters)
            }
        }

    /** Sends OFF to every heater; returns the heaters whose OFF failed. */
    private fun turnOff(heaters: Collection<Heater>): Set<UUID> =
        heaters.mapNotNull { heater ->
            callHeater(logger) { heater.off() }.fold(
                ifLeft = {
                    logger.error("Heater '${heater.uuid}' failed to turn OFF while disabling heating: $it")
                    heater.uuid
                },
                ifRight = { null }
            )
        }.toSet()
}
