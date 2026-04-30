package org.agrfesta.sh.api.services

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.utils.CacheOkResponse
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.devices.BatteryValue
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.FailureByException
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.test.mothers.aThermoHygroDataValue
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class SensorReadingsSyncServiceTest {
    private val devicesService: DevicesService = mockk()
    private val cache: SmartCache = mockk()
    private val sut = SensorReadingsSyncService(devicesService, cache)

    private fun aSensorMock(): Sensor = mockk<Sensor>().also {
        every { it.provider } returns Provider.SWITCHBOT
        every { it.deviceProviderId } returns aRandomUniqueString()
    }

    @Test
    fun `fetchAndCacheSensorData() does not interact with cache when getAllDevices() fails`() {
        every { devicesService.getAllDevices() } returns PersistenceFailure(Exception("db error")).left()

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 0) { cache.setThermoHygroOf(any(), any()) }
    }

    @Test
    fun `fetchAndCacheSensorData() does not interact with cache when device list is empty`() {
        every { devicesService.getAllDevices() } returns emptyList<DeviceDriver>().right()

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 0) { cache.setThermoHygroOf(any(), any()) }
    }

    @Test
    fun `fetchAndCacheSensorData() does not interact with cache when no sensors are present`() {
        val nonSensor: DeviceDriver = mockk()
        every { devicesService.getAllDevices() } returns listOf(nonSensor).right()

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 0) { cache.setThermoHygroOf(any(), any()) }
    }

    @Test
    fun `fetchAndCacheSensorData() caches thermo-hygro data when sensor returns ThermoHygroDataValue`() {
        val thermoHygro = aThermoHygroDataValue()
        val sensor = aSensorMock()
        every { devicesService.getAllDevices() } returns listOf(sensor).right()
        coEvery { sensor.fetchReadings() } returns thermoHygro.right()
        every { cache.setThermoHygroOf(sensor, thermoHygro.thermoHygroData) } returns CacheOkResponse

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 1) { cache.setThermoHygroOf(sensor, thermoHygro.thermoHygroData) }
    }

    @Test
    fun `fetchAndCacheSensorData() does not cache when sensor returns non-thermo-hygro reading`() {
        val batteryReading = object : BatteryValue { override val battery: Int = 50 }
        val sensor = aSensorMock()
        every { devicesService.getAllDevices() } returns listOf(sensor).right()
        coEvery { sensor.fetchReadings() } returns batteryReading.right()

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 0) { cache.setThermoHygroOf(any(), any()) }
    }

    @Test
    fun `fetchAndCacheSensorData() does not cache when sensor fetchReadings fails`() {
        val sensor = aSensorMock()
        every { devicesService.getAllDevices() } returns listOf(sensor).right()
        coEvery { sensor.fetchReadings() } returns FailureByException(RuntimeException("provider error")).left()

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 0) { cache.setThermoHygroOf(any(), any()) }
    }

    @Test
    fun `fetchAndCacheSensorData() continues processing remaining sensors when one fails`() {
        val failingSensor = aSensorMock()
        val successSensor = aSensorMock()
        val thermoHygro = aThermoHygroDataValue()
        every { devicesService.getAllDevices() } returns listOf(failingSensor, successSensor).right()
        coEvery { failingSensor.fetchReadings() } returns FailureByException(RuntimeException("provider error")).left()
        coEvery { successSensor.fetchReadings() } returns thermoHygro.right()
        every { cache.setThermoHygroOf(successSensor, thermoHygro.thermoHygroData) } returns CacheOkResponse

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 1) { cache.setThermoHygroOf(successSensor, thermoHygro.thermoHygroData) }
        verify(exactly = 0) { cache.setThermoHygroOf(failingSensor, any()) }
    }

    @Test
    fun `fetchAndCacheSensorData() caches only thermo-hygro results among mixed device and sensor outcomes`() {
        val thermoHygroSensor = aSensorMock()
        val batteryOnlySensor = aSensorMock()
        val failingSensor = aSensorMock()
        val nonSensor: DeviceDriver = mockk()
        val thermoHygro = aThermoHygroDataValue()
        val batteryReading = object : BatteryValue { override val battery: Int = 80 }
        every { devicesService.getAllDevices() } returns
            listOf(thermoHygroSensor, batteryOnlySensor, failingSensor, nonSensor).right()
        coEvery { thermoHygroSensor.fetchReadings() } returns thermoHygro.right()
        coEvery { batteryOnlySensor.fetchReadings() } returns batteryReading.right()
        coEvery { failingSensor.fetchReadings() } returns FailureByException(RuntimeException()).left()
        every { cache.setThermoHygroOf(thermoHygroSensor, thermoHygro.thermoHygroData) } returns CacheOkResponse

        runBlocking { sut.fetchAndCacheSensorData() }

        verify(exactly = 1) { cache.setThermoHygroOf(thermoHygroSensor, thermoHygro.thermoHygroData) }
        verify(exactly = 0) { cache.setThermoHygroOf(neq(thermoHygroSensor), any()) }
    }
}
