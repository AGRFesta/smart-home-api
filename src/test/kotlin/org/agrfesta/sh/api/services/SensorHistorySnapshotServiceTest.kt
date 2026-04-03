package org.agrfesta.sh.api.services

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.utils.CachedValueNotFound
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.Test

class SensorHistorySnapshotServiceTest {
    private val devicesService: DevicesService = mockk()
    private val historyDataService: SensorsHistoryDataService = mockk()
    private val cache: SmartCache = mockk()
    private val timeService: TimeService = mockk()
    private val sut = SensorHistorySnapshotService(devicesService, historyDataService, cache, timeService)

    @Test
    fun `snapshotDevicesData() does not interact with cache when getAllDto() fails`() {
        every { devicesService.getAllDto() } returns PersistenceFailure(Exception("db error")).left()

        sut.snapshotDevicesData()

        verify(exactly = 0) { cache.getThermoHygroOf(any()) }
    }

    @Test
    fun `snapshotDevicesData() does not interact with cache when device list is empty`() {
        every { devicesService.getAllDto() } returns emptyList<org.agrfesta.sh.api.domain.devices.DeviceDto>().right()

        sut.snapshotDevicesData()

        verify(exactly = 0) { cache.getThermoHygroOf(any()) }
    }

    @Test
    fun `snapshotDevicesData() does not interact with cache when no sensors are present`() {
        every { devicesService.getAllDto() } returns listOf(aDevice(), anActuator()).right()

        sut.snapshotDevicesData()

        verify(exactly = 0) { cache.getThermoHygroOf(any()) }
    }

    @Test
    fun `snapshotDevicesData() does not persist when cache has no data for sensor`() {
        val sensor = aSensor()
        every { devicesService.getAllDto() } returns listOf(sensor).right()
        every { cache.getThermoHygroOf(sensor) } returns CachedValueNotFound("some-key").left()

        sut.snapshotDevicesData()

        verify(exactly = 0) { historyDataService.persistTemperature(any(), any(), any()) }
        verify(exactly = 0) { historyDataService.persistHumidity(any(), any(), any()) }
    }

    @Test
    fun `snapshotDevicesData() persists temperature and humidity when sensor has cached data`() {
        val sensor = aSensor()
        val thermoHygro = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesService.getAllDto() } returns listOf(sensor).right()
        every { cache.getThermoHygroOf(sensor) } returns thermoHygro.right()
        every { timeService.now() } returns now
        every { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) } returns Unit.right()
        every { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) } returns Unit.right()

        sut.snapshotDevicesData()

        verify(exactly = 1) { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) }
    }

    @Test
    fun `snapshotDevicesData() persists data for each sensor with cached data`() {
        val sensor1 = aSensor()
        val sensor2 = aSensor()
        val thermoHygro1 = aRandomThermoHygroData()
        val thermoHygro2 = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesService.getAllDto() } returns listOf(sensor1, sensor2).right()
        every { cache.getThermoHygroOf(sensor1) } returns thermoHygro1.right()
        every { cache.getThermoHygroOf(sensor2) } returns thermoHygro2.right()
        every { timeService.now() } returns now
        every { historyDataService.persistTemperature(sensor1.uuid, now, thermoHygro1.temperature) } returns Unit.right()
        every { historyDataService.persistHumidity(sensor1.uuid, now, thermoHygro1.relativeHumidity) } returns Unit.right()
        every { historyDataService.persistTemperature(sensor2.uuid, now, thermoHygro2.temperature) } returns Unit.right()
        every { historyDataService.persistHumidity(sensor2.uuid, now, thermoHygro2.relativeHumidity) } returns Unit.right()

        sut.snapshotDevicesData()

        verify(exactly = 1) { historyDataService.persistTemperature(sensor1.uuid, now, thermoHygro1.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(sensor1.uuid, now, thermoHygro1.relativeHumidity) }
        verify(exactly = 1) { historyDataService.persistTemperature(sensor2.uuid, now, thermoHygro2.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(sensor2.uuid, now, thermoHygro2.relativeHumidity) }
    }

    @Test
    fun `snapshotDevicesData() continues processing remaining sensors when one has a cache miss`() {
        val missingSensor = aSensor()
        val successSensor = aSensor()
        val thermoHygro = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesService.getAllDto() } returns listOf(missingSensor, successSensor).right()
        every { cache.getThermoHygroOf(missingSensor) } returns CachedValueNotFound("missing-key").left()
        every { cache.getThermoHygroOf(successSensor) } returns thermoHygro.right()
        every { timeService.now() } returns now
        every { historyDataService.persistTemperature(successSensor.uuid, now, thermoHygro.temperature) } returns Unit.right()
        every { historyDataService.persistHumidity(successSensor.uuid, now, thermoHygro.relativeHumidity) } returns Unit.right()

        sut.snapshotDevicesData()

        verify(exactly = 0) { historyDataService.persistTemperature(missingSensor.uuid, any(), any()) }
        verify(exactly = 0) { historyDataService.persistHumidity(missingSensor.uuid, any(), any()) }
        verify(exactly = 1) { historyDataService.persistTemperature(successSensor.uuid, now, thermoHygro.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(successSensor.uuid, now, thermoHygro.relativeHumidity) }
    }

    @Test
    fun `snapshotDevicesData() does not query cache for non-sensor devices`() {
        val sensor = aSensor()
        val nonSensor = aDevice()
        val thermoHygro = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesService.getAllDto() } returns listOf(sensor, nonSensor).right()
        every { cache.getThermoHygroOf(sensor) } returns thermoHygro.right()
        every { timeService.now() } returns now
        every { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) } returns Unit.right()
        every { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) } returns Unit.right()

        sut.snapshotDevicesData()

        verify(exactly = 0) { cache.getThermoHygroOf(nonSensor) }
        verify(exactly = 1) { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) }
    }

    @Test
    fun `snapshotDevicesData() continues processing when persistTemperature returns a failure`() {
        val sensor = aSensor()
        val thermoHygro = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesService.getAllDto() } returns listOf(sensor).right()
        every { cache.getThermoHygroOf(sensor) } returns thermoHygro.right()
        every { timeService.now() } returns now
        every { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) } returns
            PersistenceFailure(Exception("db error")).left()
        every { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) } returns Unit.right()

        sut.snapshotDevicesData()

        verify(exactly = 1) { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) }
    }

    @Test
    fun `snapshotDevicesData() continues processing when persistHumidity returns a failure`() {
        val sensor = aSensor()
        val thermoHygro = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesService.getAllDto() } returns listOf(sensor).right()
        every { cache.getThermoHygroOf(sensor) } returns thermoHygro.right()
        every { timeService.now() } returns now
        every { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) } returns Unit.right()
        every { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) } returns
            PersistenceFailure(Exception("db error")).left()

        sut.snapshotDevicesData()

        verify(exactly = 1) { historyDataService.persistTemperature(sensor.uuid, now, thermoHygro.temperature) }
        verify(exactly = 1) { historyDataService.persistHumidity(sensor.uuid, now, thermoHygro.relativeHumidity) }
    }
}
