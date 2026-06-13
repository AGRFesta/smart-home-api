package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.domain.devices.BatteryValue
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.FailureByException
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
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

    private val sut = FetchSensorReadingsService(
        devicesRepository,
        listOf(factory),
        readingsRepository,
        homeStateRefreshPublisher
    )

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

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
    }

    @Test fun `execute() does not call save() when sensor returns non-thermo-hygro reading`() {
        val batteryReading = object : BatteryValue { override val battery: Int = 50 }
        val deviceRecord = aSensor()
        val sensorDriver = mockk<Sensor>()
        every { devicesRepository.getAll() } returns listOf(deviceRecord).right()
        every { factory.createDevice(deviceRecord) } returns sensorDriver
        every { sensorDriver.fetchReadings() } returns batteryReading.right()

        val result = sut.execute()

        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.save(any(), any()) }
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
        val batteryOnlyRecord = aSensor()
        val failingRecord = aSensor()
        val noFeatureRecord = aDevice(features = emptySet())
        val thermoHygroDriver = mockk<Sensor>()
        val batteryOnlyDriver = mockk<Sensor>()
        val failingDriver = mockk<Sensor>()
        val thermoHygro = aThermoHygroDataValue()
        val batteryReading = object : BatteryValue { override val battery: Int = 80 }
        every { devicesRepository.getAll() } returns
            listOf(thermoHygroRecord, batteryOnlyRecord, failingRecord, noFeatureRecord).right()
        every { factory.createDevice(thermoHygroRecord) } returns thermoHygroDriver
        every { factory.createDevice(batteryOnlyRecord) } returns batteryOnlyDriver
        every { factory.createDevice(failingRecord) } returns failingDriver
        every { thermoHygroDriver.fetchReadings() } returns thermoHygro.right()
        every { batteryOnlyDriver.fetchReadings() } returns batteryReading.right()
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
