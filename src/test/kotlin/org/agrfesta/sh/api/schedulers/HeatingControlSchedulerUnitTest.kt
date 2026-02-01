package org.agrfesta.sh.api.schedulers

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.domain.areas.AreasFactory
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.commons.SharedHeaterContext
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SharedHeater
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.schedulers.HeatingControlScheduler.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.services.heating.NamedSharedHeatingAreasStrategyService
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategy.COMFORT
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategy.ECONOMY
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class HeatingControlSchedulerUnitTest {
    private val now: Instant = Instant.now()

    private val devicesService: DevicesService = mockk()
    private val areasDao: AreaDao = mockk()
    private val areasWithDevicesDao: AreasWithDevicesDao = mockk()
    private val temperatureSettingsDao: TemperatureSettingsDao = mockk()
    private val persistedCacheService: PersistedCacheService = mockk()
    private val timeService: TimeService = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val economyStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns ECONOMY
    }
    private val comfortStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns COMFORT
    }

    private val heatingAreasService = HeatingAreasService(areasDao, temperatureSettingsDao)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)
    private val areasService = AreasService(areasDao, areasWithDevicesDao, randomGenerator, areasFactory)
    private val strategy = DynamicSharedHeatingStrategyService(
        ECONOMY, listOf(economyStrategy, comfortStrategy), persistedCacheService)
    private val sut = HeatingControlScheduler(devicesService, areasService, strategy, persistedCacheService)

    init {
        // Default behaviour
        every { timeService.now() } returns now

        every { persistedCacheService.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry("true").right()
    }

    @Test
    fun `scheduledTask() Do nothing when is disabled`() {
        every { persistedCacheService.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry("false").right()
        val sut = HeatingControlScheduler(devicesService, areasService, strategy, persistedCacheService)

        sut.scheduledTask()

        verify(exactly = 0) { devicesService.getAllDevices() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when HEATING_ENABLED_KEY is missing`() {
        every { persistedCacheService.findEntry(HEATING_ENABLED_KEY) } returns null.right()
        val sut = HeatingControlScheduler(devicesService, areasService,
            strategy, persistedCacheService)

        sut.scheduledTask()

        verify(exactly = 0) { devicesService.getAllDevices() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when fails to fetch HEATING_ENABLED_KEY from persistence`() {
        val failure = PersistenceFailure(Exception("cache fetch failure!"))
        every { persistedCacheService.findEntry(HEATING_ENABLED_KEY) } returns failure.left()
        val sut = HeatingControlScheduler(devicesService, areasService,
            this@HeatingControlSchedulerUnitTest.strategy, persistedCacheService)

        sut.scheduledTask()

        verify(exactly = 0) { devicesService.getAllDevices() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when HEATING_ENABLED_KEY is not a boolean`() {
        every { persistedCacheService.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry(aRandomUniqueString()).right()
        val sut = HeatingControlScheduler(devicesService, areasService,
            this@HeatingControlSchedulerUnitTest.strategy, persistedCacheService)

        sut.scheduledTask()

        verify(exactly = 0) { devicesService.getAllDevices() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when fails to fetch all devices`() {
        val failure = Exception("all devices fetch failure!")
        every { devicesService.getAllDevices() } returns PersistenceFailure(failure).left()

        sut.scheduledTask()

        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when there are no shared heaters`() {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = emptyList() // The only area is not connected to the heater
        )
        every { devicesService.getAllDevices() } returns listOf(sensor, heater).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)

        sut.scheduledTask()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when there are no heatable areas`() {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = emptyList() // An area with no actuators can't be heated
        )
        every { devicesService.getAllDevices() } returns listOf(sensor, heater).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)

        sut.scheduledTask()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when the area has no sensors (not heatable)`() {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = emptyList(),
            actuators = listOf(actuatorDto)
        )
        every { devicesService.getAllDevices() } returns listOf(sensor, heater).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)

        sut.scheduledTask()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when the area has misconfigured sensors (not heatable)`() {
        val sensorDto = aSensor()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        every { devicesService.getAllDevices() } returns listOf(heater).right() // Area's sensor is missing
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)

        sut.scheduledTask()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Do nothing when the area has misconfigured actuator (not heatable)`() {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        every { devicesService.getAllDevices() } returns listOf(sensor).right() // Area's heater is missing
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)

        sut.scheduledTask()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Handle heater based on default strategy when the selected strategy is missing`() {
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val strategy = DynamicSharedHeatingStrategyService(
            ECONOMY,
            listOf(economyStrategy),
            persistedCacheService
        )
        val sut = HeatingControlScheduler(devicesService, areasService, strategy, persistedCacheService)
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `scheduledTask() Do nothing when both default and selected strategies services are missing`() {
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val strategy = DynamicSharedHeatingStrategyService(ECONOMY, emptyList(), persistedCacheService)
        val sut = HeatingControlScheduler(devicesService, areasService, strategy, persistedCacheService)
        givenHeatableArea()

        sut.scheduledTask()

        verify(exactly = 1) { devicesService.getAllDevices() }
        verify(exactly = 1) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `scheduledTask() Handle heater based on economy strategy`() {
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(ECONOMY.name).right()
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `scheduledTask() Handle heater based on default strategy when selected strategy is not valid`() {
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(aRandomUniqueString()).right()
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `scheduledTask() Handle heater based on default strategy when there is no selected strategy`() {
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns PersistedCacheEntryNotFound.left()
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `scheduledTask() Handle heater based on default strategy when fails to fetch selected strategy`() {
        val failure = PersistenceFailure(Exception("cache fetch failure!"))
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns failure.left()
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `scheduledTask() Handle heater based on comfort strategy`() {
        every { persistedCacheService.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(comfortStrategy)
    }

    @Test
    fun `scheduledTask() Handle heater based on strategy name ignoring case`() {
        every {
            persistedCacheService.getEntry(HEATING_STRATEGY_KEY)
        } returns CacheEntry(COMFORT.name.lowercase()).right()
        val testData = givenHeatableArea()

        sut.scheduledTask()

        testData.verifyHandledBy(comfortStrategy)
    }

    private fun givenHeatableArea(): Pair<AreaDtoWithDevices, SharedHeater> {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        every { devicesService.getAllDevices() } returns listOf(sensor, heater).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)
        return areaDto to heater
    }

    private fun Pair<AreaDtoWithDevices, SharedHeater>.verifyHandledBy(
        strategy: NamedSharedHeatingAreasStrategyService
    ) {
        val contextSlot = slot<SharedHeaterContext>()

        coVerify(exactly = 1) { strategy.handleHeatingFor(capture(contextSlot)) }
        val capturedContext = contextSlot.captured
        capturedContext.heater shouldBe second
        capturedContext.areas.map { it.uuid }.shouldContainExactlyInAnyOrder(first.uuid)
    }

    private fun verifyStrategiesNoInteractions() {
        coVerify(exactly = 0) { economyStrategy.handleHeatingFor(any()) }
        coVerify(exactly = 0) { comfortStrategy.handleHeatingFor(any()) }
    }

    private fun DeviceDto.toSensorMockk(): Sensor {
        val sensor: Sensor = mockk()
        every { sensor.uuid } returns uuid
        return sensor
    }

    private fun DeviceDto.toSharedHeaterMockk(): SharedHeater {
        val heater: SharedHeater = mockk(relaxed = true)
        every { heater.uuid } returns uuid
        return heater
    }

}
