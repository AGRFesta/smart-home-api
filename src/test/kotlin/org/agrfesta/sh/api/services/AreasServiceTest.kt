package org.agrfesta.sh.api.services

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.util.*
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.areas.AreaImpl
import org.agrfesta.sh.api.core.domain.areas.AreasFactory
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.areas.MonitoredClimateArea
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.UnitOfWork
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TemperatureSettingsRepository
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.utils.TimeService
import org.junit.jupiter.api.Test

class AreasServiceTest {
    private val areasRepository: AreasRepository = mockk()
    private val areasWithDevicesRepository: AreasWithDevicesRepository = mockk()
    private val timeService: TimeService = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val unitOfWork: UnitOfWork = mockk()
    private val heatingAreasService = HeatingAreasService(areasRepository, temperatureSettingsRepository, unitOfWork)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)

    private val sut = AreasService(areasWithDevicesRepository, areasFactory)

    // getAllAreasWithDevices()

    @Test
    fun `getAllAreasWithDevices() Returns empty collection when no areas exist`() {
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<Nothing>().right()

        sut.getAllAreasWithDevices()
            .shouldBeRight()
            .shouldBeEmpty()
    }

    @Test
    fun `getAllAreasWithDevices() Returns areas from repository`() {
        val areaA = anAreaDtoWithDevices()
        val areaB = anAreaDtoWithDevices()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaA, areaB).right()

        sut.getAllAreasWithDevices()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getAllAreasWithDevices() Returns PersistenceFailure when repository fails`() {
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns
            PersistenceFailure(Exception("db error")).left()

        sut.getAllAreasWithDevices()
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // getAllAreas()

    @Test
    fun `getAllAreas() Returns PersistenceFailure when repository fails`() {
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns
            PersistenceFailure(Exception("db error")).left()

        sut.getAllAreas(emptyMap())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `getAllAreas() Returns empty collection when no areas exist`() {
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<Nothing>().right()

        sut.getAllAreas(emptyMap())
            .shouldBeRight()
            .shouldBeEmpty()
    }

    @Test
    fun `getAllAreas() Returns AreaImpl when area has no devices in registry`() {
        val area = anAreaDtoWithDevices()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.getAllAreas(emptyMap()).shouldBeRight()

        result.shouldHaveSize(1)
        result.first().shouldBeInstanceOf<AreaImpl>()
    }

    @Test
    fun `getAllAreas() Returns MonitoredClimateArea when area has sensor in registry`() {
        val sensorDto = aSensor()
        val sensor: Sensor = mockk()
        every { sensor.uuid } returns sensorDto.uuid
        val area = anAreaDtoWithDevices(sensors = listOf(sensorDto))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.getAllAreas(mapOf(sensorDto.uuid to sensor)).shouldBeRight()

        result.shouldHaveSize(1)
        result.first().shouldBeInstanceOf<MonitoredClimateArea>()
    }

    @Test
    fun `getAllAreas() Returns HeatableArea when area has sensor and heater in registry`() {
        val sensorDto = aSensor()
        val sensor: Sensor = mockk()
        every { sensor.uuid } returns sensorDto.uuid
        val actuatorDto = anActuator()
        val heater: SharedHeater = mockk(relaxed = true)
        every { heater.uuid } returns actuatorDto.uuid
        val area = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.getAllAreas(mapOf(sensorDto.uuid to sensor, actuatorDto.uuid to heater)).shouldBeRight()

        result.shouldHaveSize(1)
        result.first().shouldBeInstanceOf<HeatableArea>()
    }

    @Test
    fun `getAllAreas() Returns AreaImpl when area sensors are not found in registry`() {
        val sensorDto = aSensor()
        val area = anAreaDtoWithDevices(sensors = listOf(sensorDto))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.getAllAreas(emptyMap()).shouldBeRight()

        result.shouldHaveSize(1)
        result.first().shouldBeInstanceOf<AreaImpl>()
    }

}
