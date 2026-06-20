package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.inbounds.InspectDeviceUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Inspectable
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DiagnosticsNotSupported
import org.agrfesta.sh.api.core.domain.failures.DiagnosticsProviderFailure
import org.agrfesta.sh.api.core.domain.failures.InspectDeviceFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class InspectDeviceService(
    private val devicesRepository: DevicesRepository,
    private val deviceFactories: Set<ProviderDevicesFactory>
) : InspectDeviceUseCase {

    override fun execute(deviceId: UUID): Either<InspectDeviceFailure, String> =
        devicesRepository.getDeviceById(deviceId)
            .mapLeft { failure ->
                when (failure) {
                    is DeviceNotFound -> failure
                    DeviceRepositoryError -> DeviceRepositoryError
                }
            }
            .flatMap { device ->
                val factory = deviceFactories.firstOrNull { it.provider == device.provider }
                    ?: return@flatMap DiagnosticsNotSupported.left()
                val driver = factory.createDevice(device)
                if (driver !is Inspectable) {
                    return@flatMap DiagnosticsNotSupported.left()
                }
                driver.inspect()
                    .mapLeft { failure ->
                        when (failure) {
                            is DevicesProviderError -> DiagnosticsProviderFailure(failure.exception.message)
                        }
                    }
            }
}
