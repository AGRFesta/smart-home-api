package org.agrfesta.sh.api.core.application.usecases.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.GetAcStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.AirConditioner
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.AcState
import org.agrfesta.sh.api.core.domain.failures.AcProviderFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.GetAcStateFailure
import org.agrfesta.sh.api.core.domain.failures.NotAnAirConditioner
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetAcStateService(
    private val devicesRepository: DevicesRepository,
    private val deviceFactories: Set<ProviderDevicesFactory>
) : GetAcStateUseCase {

    override fun execute(deviceId: UUID): Either<GetAcStateFailure, AcState> =
        devicesRepository.getDeviceById(deviceId)
            .mapLeft { failure ->
                when (failure) {
                    is DeviceNotFound -> failure
                    DeviceRepositoryError -> DeviceRepositoryError
                }
            }
            .flatMap { device ->
                val factory = deviceFactories.firstOrNull { it.provider == device.provider }
                    ?: return@flatMap NotAnAirConditioner.left()
                val driver = factory.createDevice(device) as? AirConditioner
                    ?: return@flatMap NotAnAirConditioner.left()
                driver.getState().mapLeft { AcProviderFailure(it.message) }
            }
}
