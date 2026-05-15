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
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsHistoryDataRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupError
import org.agrfesta.sh.api.core.domain.failures.SensorHistoryRepositoryError
import org.agrfesta.sh.api.core.domain.failures.SnapshotSensorHistoryError
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.Test

class SnapshotSensorHistoryServiceTest {
    private val devicesRepository: DevicesRepository = mockk()
    private val readingsRepository: SensorsCurrentReadingsRepository = mockk()
    private val historyRepository: SensorsHistoryDataRepository = mockk()
    private val timeProvider: TimeProvider = mockk()

    private val sut = SnapshotSensorHistoryService(devicesRepository, readingsRepository, historyRepository, timeProvider)

    @Test fun `execute() returns Left(SnapshotSensorHistoryError) when device repository getAll() fails`() {
        // Given
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<SnapshotSensorHistoryError>()
        verify(exactly = 0) { readingsRepository.findBy(any()) }
    }

    @Test fun `execute() returns Right(Unit) and does not interact with readings repository when device list is empty`() {
        // Given
        every { devicesRepository.getAll() } returns emptyList<Device>().right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.findBy(any()) }
    }

    @Test fun `execute() returns Right(Unit) and does not interact with readings repository when no sensor is present`() {
        // Given
        every { devicesRepository.getAll() } returns listOf(aDevice(features = emptySet()), anActuator()).right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.findBy(any()) }
    }

    @Test fun `execute() returns Right(Unit) and does not interact with history repository when readings lookup returns null for a sensor`() {
        // Given
        val sensor = aSensor()
        every { devicesRepository.getAll() } returns listOf(sensor).right()
        every { readingsRepository.findBy(sensor) } returns null.right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 0) { historyRepository.persistTemperature(any(), any(), any()) }
        verify(exactly = 0) { historyRepository.persistHumidity(any(), any(), any()) }
    }

    @Test fun `execute() returns Right(Unit) and does not interact with history repository when readings lookup fails for a sensor`() {
        // Given
        val sensor = aSensor()
        every { devicesRepository.getAll() } returns listOf(sensor).right()
        every { readingsRepository.findBy(sensor) } returns ReadingsLookupError(Exception("cache error")).left()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 0) { historyRepository.persistTemperature(any(), any(), any()) }
        verify(exactly = 0) { historyRepository.persistHumidity(any(), any(), any()) }
    }

    @Test fun `execute() returns Right(Unit) and persists temperature and humidity when a sensor has readings available`() {
        // Given
        val sensor = aSensor()
        val readings = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesRepository.getAll() } returns listOf(sensor).right()
        every { readingsRepository.findBy(sensor) } returns readings.right()
        every { timeProvider.now() } returns now
        every { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) } returns Unit.right()
        every { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) } returns Unit.right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 1) { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) }
    }

    @Test fun `execute() returns Right(Unit) and persists data independently for each sensor`() {
        // Given
        val sensor1 = aSensor()
        val sensor2 = aSensor()
        val readings1 = aRandomThermoHygroData()
        val readings2 = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesRepository.getAll() } returns listOf(sensor1, sensor2).right()
        every { readingsRepository.findBy(sensor1) } returns readings1.right()
        every { readingsRepository.findBy(sensor2) } returns readings2.right()
        every { timeProvider.now() } returns now
        every { historyRepository.persistTemperature(sensor1.uuid, now, readings1.temperature) } returns Unit.right()
        every { historyRepository.persistHumidity(sensor1.uuid, now, readings1.relativeHumidity) } returns Unit.right()
        every { historyRepository.persistTemperature(sensor2.uuid, now, readings2.temperature) } returns Unit.right()
        every { historyRepository.persistHumidity(sensor2.uuid, now, readings2.relativeHumidity) } returns Unit.right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 1) { historyRepository.persistTemperature(sensor1.uuid, now, readings1.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(sensor1.uuid, now, readings1.relativeHumidity) }
        verify(exactly = 1) { historyRepository.persistTemperature(sensor2.uuid, now, readings2.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(sensor2.uuid, now, readings2.relativeHumidity) }
    }

    @Test fun `execute() continues processing remaining sensors when one has no cached readings`() {
        // Given
        val missingSensor = aSensor()
        val successSensor = aSensor()
        val readings = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesRepository.getAll() } returns listOf(missingSensor, successSensor).right()
        every { readingsRepository.findBy(missingSensor) } returns null.right()
        every { readingsRepository.findBy(successSensor) } returns readings.right()
        every { timeProvider.now() } returns now
        every { historyRepository.persistTemperature(successSensor.uuid, now, readings.temperature) } returns Unit.right()
        every { historyRepository.persistHumidity(successSensor.uuid, now, readings.relativeHumidity) } returns Unit.right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 0) { historyRepository.persistTemperature(missingSensor.uuid, any(), any()) }
        verify(exactly = 0) { historyRepository.persistHumidity(missingSensor.uuid, any(), any()) }
        verify(exactly = 1) { historyRepository.persistTemperature(successSensor.uuid, now, readings.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(successSensor.uuid, now, readings.relativeHumidity) }
    }

    @Test fun `execute() does not query readings repository for non-sensor devices`() {
        // Given
        val sensor = aSensor()
        val nonSensor = aDevice(features = emptySet())
        val readings = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesRepository.getAll() } returns listOf(sensor, nonSensor).right()
        every { readingsRepository.findBy(sensor) } returns readings.right()
        every { timeProvider.now() } returns now
        every { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) } returns Unit.right()
        every { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) } returns Unit.right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 0) { readingsRepository.findBy(nonSensor) }
        verify(exactly = 1) { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) }
    }

    @Test fun `execute() continues processing when persistTemperature fails for a sensor`() {
        // Given
        val sensor = aSensor()
        val readings = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesRepository.getAll() } returns listOf(sensor).right()
        every { readingsRepository.findBy(sensor) } returns readings.right()
        every { timeProvider.now() } returns now
        every { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) } returns
            SensorHistoryRepositoryError.left()
        every { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) } returns Unit.right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 1) { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) }
    }

    @Test fun `execute() continues processing when persistHumidity fails for a sensor`() {
        // Given
        val sensor = aSensor()
        val readings = aRandomThermoHygroData()
        val now = nowNoMills()
        every { devicesRepository.getAll() } returns listOf(sensor).right()
        every { readingsRepository.findBy(sensor) } returns readings.right()
        every { timeProvider.now() } returns now
        every { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) } returns Unit.right()
        every { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) } returns
            SensorHistoryRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight()
        verify(exactly = 1) { historyRepository.persistTemperature(sensor.uuid, now, readings.temperature) }
        verify(exactly = 1) { historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity) }
    }

}
