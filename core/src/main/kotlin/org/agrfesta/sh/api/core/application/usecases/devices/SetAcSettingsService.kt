package org.agrfesta.sh.api.core.application.usecases.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.SetAcSettingsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.AirConditioner
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate
import org.agrfesta.sh.api.core.domain.failures.AcProviderFailure
import org.agrfesta.sh.api.core.domain.failures.AcSettingRejected
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.InvalidAcSetting
import org.agrfesta.sh.api.core.domain.failures.NotAnAirConditioner
import org.agrfesta.sh.api.core.domain.failures.SetAcSettingsFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SetAcSettingsService(
    private val devicesRepository: DevicesRepository,
    private val deviceFactories: Set<ProviderDevicesFactory>
) : SetAcSettingsUseCase {

    override fun execute(deviceId: UUID, update: AcSettingsUpdate): Either<SetAcSettingsFailure, Unit> =
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
                driver.updateSettings(update).mapLeft { failure ->
                    when (failure) {
                        is AcSettingRejected -> InvalidAcSetting(failure.reason)
                        else -> AcProviderFailure(failure.message)
                    }
                }
            }
}
