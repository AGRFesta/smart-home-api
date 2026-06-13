package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDevicesUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.GetDevicesFailure
import org.springframework.stereotype.Service

@Service
class GetDevicesService(
    private val devicesRepository: DevicesRepository
) : GetDevicesUseCase {

    override fun execute(
        provider: Provider?,
        status: DeviceStatus?,
        feature: DeviceFeature?
    ): Either<GetDevicesFailure, Collection<Device>> =
        devicesRepository.getDevices(provider, status, feature)
}
