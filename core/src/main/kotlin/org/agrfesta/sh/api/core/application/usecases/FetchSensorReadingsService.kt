package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.inbounds.FetchSensorReadingsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.BatteryPowered
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Sensor
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
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
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher,
    private val deviceBatteryRepository: DeviceBatteryRepository
) : FetchSensorReadingsUseCase {

    private val logger by LoggerDelegate()
    private val factories = providerDevicesFactories.associateBy { it.provider }

    override fun execute(): Either<FetchSensorReadingsFailure, Unit> {
        val devices = devicesRepository.getAll()
            .mapLeft { FetchSensorReadingsError }
            .getOrElse { return it.left() }
        devices.forEach { device ->
            val driver = factories[device.provider]?.createDevice(device) ?: return@forEach
            collectReadings(device, driver)
            collectBattery(device, driver)
        }
        homeStateRefreshPublisher.publish()
        return Unit.right()
    }

    private fun collectReadings(device: Device, driver: DeviceDriver) {
        if (!device.features.contains(DeviceFeature.SENSOR) || driver !is Sensor) return
        driver.fetchReadings()
            .onRight { readings ->
                if (readings is ThermoHygroDataValue) {
                    readingsRepository.save(driver, readings.thermoHygroData)
                        .onLeft { failure ->
                            logger.error("Failed to save readings for device ${device.uuid}: $failure")
                        }
                }
            }
            .onLeft { failure ->
                logger.error("Failed to fetch readings for device ${device.uuid}: $failure")
            }
    }

    private fun collectBattery(device: Device, driver: DeviceDriver) {
        if (driver !is BatteryPowered) return
        driver.batteryLevel()
            .onRight { level ->
                deviceBatteryRepository.save(driver, level)
                    .onLeft { failure ->
                        logger.error("Failed to save battery for device ${device.uuid}: $failure")
                    }
            }
            .onLeft { failure ->
                logger.error("Failed to fetch battery for device ${device.uuid}: $failure")
            }
    }
}
