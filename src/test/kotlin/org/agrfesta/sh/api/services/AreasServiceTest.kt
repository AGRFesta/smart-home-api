package org.agrfesta.sh.api.services

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.util.*
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.sh.api.domain.areas.AreaImpl
import org.agrfesta.sh.api.domain.areas.AreasFactory
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.areas.MonitoredClimateArea
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SharedHeater
import org.agrfesta.sh.api.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.sh.api.persistence.utils.TransactionRunner
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class AreasServiceTest {
    private val areasDao: AreasDao = mockk()
    private val areasWithDevicesDao: AreasWithDevicesDao = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val timeService: TimeService = mockk()
    private val temperatureSettingsDao: TemperatureSettingsDao = mockk()
    private val transactionRunner: TransactionRunner = mockk()
    private val heatingAreasService = HeatingAreasService(areasDao, temperatureSettingsDao, transactionRunner)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)

    private val sut = AreasService(areasDao, areasWithDevicesDao, randomGenerator, areasFactory)

    // createArea()

    @Test
    fun `createArea() Returns created area on success`() {
        val uuid = UUID.randomUUID()
        val name = aRandomUniqueString()
        every { randomGenerator.uuid() } returns uuid
        every { areasDao.save(any()) } returns Unit.right()

        val result = sut.createArea(name).shouldBeRight()

        result.uuid shouldBe uuid
        result.name shouldBe name
        result.isIndoor shouldBe true
    }

    @Test
    fun `createArea() Creates indoor area by default`() {
        val uuid = UUID.randomUUID()
        every { randomGenerator.uuid() } returns uuid
        every { areasDao.save(any()) } returns Unit.right()

        sut.createArea(aRandomUniqueString()).shouldBeRight().isIndoor shouldBe true
    }

    @Test
    fun `createArea() Creates outdoor area when isIndoor is false`() {
        val uuid = UUID.randomUUID()
        every { randomGenerator.uuid() } returns uuid
        every { areasDao.save(any()) } returns Unit.right()

        sut.createArea(aRandomUniqueString(), isIndoor = false).shouldBeRight().isIndoor shouldBe false
    }

    @Test
    fun `createArea() Returns AreaNameConflict when area name already exists`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { areasDao.save(any()) } returns AreaNameConflict.left()

        sut.createArea(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBe(AreaNameConflict)
    }

    @Test
    fun `createArea() Returns PersistenceFailure when dao fails`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { areasDao.save(any()) } returns PersistenceFailure(Exception("db error")).left()

        sut.createArea(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // getAllAreasWithDevices()

    @Test
    fun `getAllAreasWithDevices() Returns empty collection when no areas exist`() {
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns emptyList<Nothing>().right()

        sut.getAllAreasWithDevices()
            .shouldBeRight()
            .shouldBeEmpty()
    }

    @Test
    fun `getAllAreasWithDevices() Returns areas from dao`() {
        val areaA = anAreaDtoWithDevices()
        val areaB = anAreaDtoWithDevices()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaA, areaB).right()

        sut.getAllAreasWithDevices()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getAllAreasWithDevices() Returns PersistenceFailure when dao fails`() {
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns
            PersistenceFailure(Exception("db error")).left()

        sut.getAllAreasWithDevices()
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // getAllAreas()

    @Test
    fun `getAllAreas() Returns PersistenceFailure when dao fails`() {
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns
            PersistenceFailure(Exception("db error")).left()

        sut.getAllAreas(emptyMap())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `getAllAreas() Returns empty collection when no areas exist`() {
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns emptyList<Nothing>().right()

        sut.getAllAreas(emptyMap())
            .shouldBeRight()
            .shouldBeEmpty()
    }

    @Test
    fun `getAllAreas() Returns AreaImpl when area has no devices in registry`() {
        val area = anAreaDtoWithDevices()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(area).right()

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
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(area).right()

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
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.getAllAreas(mapOf(sensorDto.uuid to sensor, actuatorDto.uuid to heater)).shouldBeRight()

        result.shouldHaveSize(1)
        result.first().shouldBeInstanceOf<HeatableArea>()
    }

    @Test
    fun `getAllAreas() Returns AreaImpl when area sensors are not found in registry`() {
        val sensorDto = aSensor()
        val area = anAreaDtoWithDevices(sensors = listOf(sensorDto))
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.getAllAreas(emptyMap()).shouldBeRight()

        result.shouldHaveSize(1)
        result.first().shouldBeInstanceOf<AreaImpl>()
    }

}
