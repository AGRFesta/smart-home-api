package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.domain.devices.BatteryPowered
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.FailureByException
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.failures.BatterySaveError
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.FetchSensorReadingsError
import org.agrfesta.sh.api.core.domain.failures.SensorReadingsSaveError
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.test.mothers.aThermoHygroDataValue
import org.junit.jupiter.api.Test

class FetchSensorReadingsServiceTest {
    private val devicesRepository: DevicesRepository = mockk()
    private val factory: ProviderDevicesFactory = mockk<ProviderDevicesFactory>().also {
        every { it.provider } returns Provider.SWITCHBOT
    }
    private val readingsRepository: SensorsCurrentReadingsRepository = mockk()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)
    private val deviceBatteryRepository: DeviceBatteryRepository = mockk()

    private val sut = FetchSensorReadingsService(
        devicesRepository,
        listOf(factory),
        readingsRepository,
        homeStateRefreshPublisher,
        deviceBatteryRepository
    )

    /** A driver that is battery-powered without being a [Sensor]. */
    private interface BatteryDriver : DeviceDriver, BatteryPowered

    /** A driver that is both a [Sensor] and battery-powered. */
    private interface SensorBatteryDriver : Sensor, BatteryPowered

    @Test
    fun `execute() returns Left(FetchSensorReadingsError) when getAll() fails`() {
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        val result = sut.execute()

        result.shouldBeLeft().shouldBeInstanceOf<FetchSensorReadingsError>()
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
    }

    @Test
    fun `execute() returns Right(Unit) and does not interact with readings repository when device list is empty`() {
        every { devicesRepository.getAll() } returns emptyList<Device>().right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
    }

    @Test
    fun `execute() returns Right(Unit) and does not interact with readings repository when no device is a sensor`() {
        val noFeatureDevice = aDevice(features = emptySet())
        val actuator = anActuator()
        every { devicesRepository.getAll() } returns listOf(noFeatureDevice, actuator).right()
        every { factory.createDevice(noFeatureDevice) } returns mockk<DeviceDriver>()
        every { factory.createDevice(actuator) } returns mockk<DeviceDriver>()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
        verify(exactly = 0) { deviceBatteryRepository.save(any(), any()) }
    }

    @Test fun `execute() does not call save() when sensor fetchReadings() fails`() {
        val deviceRecord = aSensor()
        val sensorDriver = mockk<Sensor>()
        every { devicesRepository.getAll() } returns listOf(deviceRecord).right()
        every { factory.createDevice(deviceRecord) } returns sensorDriver
        every { sensorDriver.fetchReadings() } returns FailureByException(RuntimeException("provider error")).left()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
    }

    @Test fun `execute() continues processing remaining sensors when one fetchReadings() fails`() {
        val failingRecord = aSensor()
        val successRecord = aSensor()
        val failingDriver = mockk<Sensor>()
        val successDriver = mockk<Sensor>()
        val thermoHygro = aThermoHygroDataValue()
        every { devicesRepository.getAll() } returns listOf(failingRecord, successRecord).right()
        every { factory.createDevice(failingRecord) } returns failingDriver
        every { factory.createDevice(successRecord) } returns successDriver
        every { failingDriver.fetchReadings() } returns FailureByException(RuntimeException("provider error")).left()
        every { successDriver.fetchReadings() } returns thermoHygro.right()
        every { readingsRepository.save(successDriver, thermoHygro.thermoHygroData) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { readingsRepository.save(successDriver, thermoHygro.thermoHygroData) }
        verify(exactly = 0) { readingsRepository.save(failingDriver, any()) }
    }

    @Test fun `execute() saves only thermo-hygro results among mixed device and sensor outcomes`() {
        val thermoHygroRecord = aSensor()
        val failingRecord = aSensor()
        val noFeatureRecord = aDevice(features = emptySet())
        val thermoHygroDriver = mockk<Sensor>()
        val failingDriver = mockk<Sensor>()
        val thermoHygro = aThermoHygroDataValue()
        every { devicesRepository.getAll() } returns
            listOf(thermoHygroRecord, failingRecord, noFeatureRecord).right()
        every { factory.createDevice(thermoHygroRecord) } returns thermoHygroDriver
        every { factory.createDevice(failingRecord) } returns failingDriver
        every { factory.createDevice(noFeatureRecord) } returns mockk<DeviceDriver>()
        every { thermoHygroDriver.fetchReadings() } returns thermoHygro.right()
        every { failingDriver.fetchReadings() } returns FailureByException(RuntimeException()).left()
        every { readingsRepository.save(thermoHygroDriver, thermoHygro.thermoHygroData) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { readingsRepository.save(thermoHygroDriver, thermoHygro.thermoHygroData) }
        verify(exactly = 0) { readingsRepository.save(neq(thermoHygroDriver), any()) }
    }

    @Test fun `execute() continues processing when save() fails for one sensor`() {
        val failingSaveRecord = aSensor()
        val successRecord = aSensor()
        val failingSaveDriver = mockk<Sensor>()
        val successDriver = mockk<Sensor>()
        val thermoHygro1 = aThermoHygroDataValue()
        val thermoHygro2 = aThermoHygroDataValue()
        every { devicesRepository.getAll() } returns listOf(failingSaveRecord, successRecord).right()
        every { factory.createDevice(failingSaveRecord) } returns failingSaveDriver
        every { factory.createDevice(successRecord) } returns successDriver
        every { failingSaveDriver.fetchReadings() } returns thermoHygro1.right()
        every { successDriver.fetchReadings() } returns thermoHygro2.right()
        every { readingsRepository.save(failingSaveDriver, thermoHygro1.thermoHygroData) } returns
            SensorReadingsSaveError(RuntimeException("cache error")).left()
        every { readingsRepository.save(successDriver, thermoHygro2.thermoHygroData) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { readingsRepository.save(failingSaveDriver, thermoHygro1.thermoHygroData) }
        verify(exactly = 1) { readingsRepository.save(successDriver, thermoHygro2.thermoHygroData) }
    }

    @Test fun `execute() publishes home state refresh after a successful fetch cycle`() {
        every { devicesRepository.getAll() } returns emptyList<Device>().right()

        sut.execute()

        verify { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() does not publish home state refresh when the fetch cycle fails`() {
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        sut.execute()

        verify(exactly = 0) { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() saves the battery level for a BatteryPowered device`() {
        val deviceRecord = aSensor()
        val driver = mockk<BatteryDriver>()
        every { devicesRepository.getAll() } returns listOf(deviceRecord).right()
        every { factory.createDevice(deviceRecord) } returns driver
        every { driver.batteryLevel() } returns 88.right()
        every { deviceBatteryRepository.save(driver, 88) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { deviceBatteryRepository.save(driver, 88) }
    }

    @Test fun `execute() does not save battery for a device whose driver is not BatteryPowered`() {
        val deviceRecord = aSensor()
        val sensorDriver = mockk<Sensor>()
        val thermoHygro = aThermoHygroDataValue()
        every { devicesRepository.getAll() } returns listOf(deviceRecord).right()
        every { factory.createDevice(deviceRecord) } returns sensorDriver
        every { sensorDriver.fetchReadings() } returns thermoHygro.right()
        every { readingsRepository.save(sensorDriver, thermoHygro.thermoHygroData) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 0) { deviceBatteryRepository.save(any(), any()) }
    }

    @Test fun `execute() collects battery for a non-sensor BatteryPowered device`() {
        val actuatorRecord = anActuator()
        val driver = mockk<BatteryDriver>()
        every { devicesRepository.getAll() } returns listOf(actuatorRecord).right()
        every { factory.createDevice(actuatorRecord) } returns driver
        every { driver.batteryLevel() } returns 73.right()
        every { deviceBatteryRepository.save(driver, 73) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { deviceBatteryRepository.save(driver, 73) }
    }

    @Test fun `execute() continues processing remaining devices when batteryLevel() fails for one`() {
        val failingRecord = aSensor()
        val successRecord = anActuator()
        val failingDriver = mockk<BatteryDriver>()
        val successDriver = mockk<BatteryDriver>()
        every { devicesRepository.getAll() } returns listOf(failingRecord, successRecord).right()
        every { factory.createDevice(failingRecord) } returns failingDriver
        every { factory.createDevice(successRecord) } returns successDriver
        every { failingDriver.batteryLevel() } returns DevicesProviderError(RuntimeException("provider error")).left()
        every { successDriver.batteryLevel() } returns 60.right()
        every { deviceBatteryRepository.save(successDriver, 60) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { deviceBatteryRepository.save(successDriver, 60) }
        verify(exactly = 0) { deviceBatteryRepository.save(failingDriver, any()) }
    }

    @Test fun `execute() continues processing when battery save() fails for one device`() {
        val failingSaveRecord = aSensor()
        val successRecord = anActuator()
        val failingSaveDriver = mockk<BatteryDriver>()
        val successDriver = mockk<BatteryDriver>()
        every { devicesRepository.getAll() } returns listOf(failingSaveRecord, successRecord).right()
        every { factory.createDevice(failingSaveRecord) } returns failingSaveDriver
        every { factory.createDevice(successRecord) } returns successDriver
        every { failingSaveDriver.batteryLevel() } returns 20.right()
        every { successDriver.batteryLevel() } returns 60.right()
        every { deviceBatteryRepository.save(failingSaveDriver, 20) } returns
            BatterySaveError(RuntimeException("cache error")).left()
        every { deviceBatteryRepository.save(successDriver, 60) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { deviceBatteryRepository.save(failingSaveDriver, 20) }
        verify(exactly = 1) { deviceBatteryRepository.save(successDriver, 60) }
    }

    @Test fun `execute() collects battery even when the same driver's fetchReadings() fails`() {
        val deviceRecord = aSensor()
        val driver = mockk<SensorBatteryDriver>()
        every { devicesRepository.getAll() } returns listOf(deviceRecord).right()
        every { factory.createDevice(deviceRecord) } returns driver
        every { driver.fetchReadings() } returns FailureByException(RuntimeException("provider error")).left()
        every { driver.batteryLevel() } returns 42.right()
        every { deviceBatteryRepository.save(driver, 42) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { deviceBatteryRepository.save(driver, 42) }
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
    }

    @Test fun `execute() calls save() with thermo-hygro data when sensor returns ThermoHygroDataValue`() {
        val deviceRecord = aSensor()
        val sensorDriver = mockk<Sensor>()
        val thermoHygro = aThermoHygroDataValue()
        every { devicesRepository.getAll() } returns listOf(deviceRecord).right()
        every { factory.createDevice(deviceRecord) } returns sensorDriver
        every { sensorDriver.fetchReadings() } returns thermoHygro.right()
        every { readingsRepository.save(sensorDriver, thermoHygro.thermoHygroData) } returns Unit.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 1) { readingsRepository.save(sensorDriver, thermoHygro.thermoHygroData) }
    }
}
