package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.inbounds.FetchSensorReadingsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.core.domain.failures.FetchSensorReadingsError
import org.agrfesta.sh.api.core.domain.failures.FetchSensorReadingsFailure
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class FetchSensorReadingsService(
    private val devicesRepository: DevicesRepository,
    providerDevicesFactories: Collection<ProviderDevicesFactory>,
    private val readingsRepository: SensorsCurrentReadingsRepository,
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher
) : FetchSensorReadingsUseCase {

    private val logger by LoggerDelegate()
    private val factories = providerDevicesFactories.associateBy { it.provider }

    override fun execute(): Either<FetchSensorReadingsFailure, Unit> {
        val devices = devicesRepository.getAll()
            .mapLeft { FetchSensorReadingsError }
            .getOrElse { return it.left() }
        devices
            .filter { it.features.contains(DeviceFeature.SENSOR) }
            .forEach { device ->
                val driver = factories[device.provider]?.createDevice(device) ?: return@forEach
                if (driver is Sensor) {
                    val readings = driver.fetchReadings()
                        .getOrElse { failure ->
                            logger.error("Failed to fetch readings for device ${device.uuid}: $failure")
                            return@forEach
                        }
                    if (readings is ThermoHygroDataValue) {
                        readingsRepository.save(driver, readings.thermoHygroData)
                            .onLeft { failure ->
                                logger.error("Failed to save readings for device ${device.uuid}: $failure")
                            }
                    }
                }
            }
        homeStateRefreshPublisher.publish()
        return Unit.right()
    }
}
