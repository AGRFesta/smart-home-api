package org.agrfesta.sh.api.services.heating

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
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SharedHeater
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.UnitOfWork
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.services.heating.HeatingOrchestrationService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategy.COMFORT
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategy.ECONOMY
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class HeatingOrchestrationServiceTest {
    private val now: Instant = Instant.now()

    private val devicesDao: DevicesDao = mockk()
    private val factory: ProviderDevicesFactory = mockk {
        every { provider } returns Provider.SWITCHBOT
    }
    private val areasDao: AreasDao = mockk()
    private val areasWithDevicesDao: AreasWithDevicesDao = mockk()
    private val temperatureSettingsDao: TemperatureSettingsDao = mockk()
    private val cacheDao: CacheDao = mockk()
    private val timeService: TimeService = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val unitOfWork: UnitOfWork = mockk()
    private val economyStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns ECONOMY
    }
    private val comfortStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns COMFORT
    }

    private val devicesService = DevicesService(devicesDao, randomGenerator, listOf(factory))
    private val heatingAreasService = HeatingAreasService(areasDao, temperatureSettingsDao, unitOfWork)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)
    private val areasService = AreasService(areasDao, areasWithDevicesDao, randomGenerator, areasFactory)
    private val strategy = DynamicSharedHeatingStrategyService(
        ECONOMY, listOf(economyStrategy, comfortStrategy), cacheDao)

    private val sut = HeatingOrchestrationService(devicesService, areasService, strategy, cacheDao)

    init {
        every { timeService.now() } returns now
        every { cacheDao.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry("true").right()
        every { devicesDao.getAll() } returns emptyList<DeviceDto>().right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns emptyList<AreaDtoWithDevices>().right()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when is disabled`() {
        every { cacheDao.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry("false").right()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesDao.getAll() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when HEATING_ENABLED_KEY is missing`() {
        every { cacheDao.findEntry(HEATING_ENABLED_KEY) } returns null.right()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesDao.getAll() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when fails to fetch HEATING_ENABLED_KEY from persistence`() {
        val failure = PersistenceFailure(Exception("cache fetch failure!"))
        every { cacheDao.findEntry(HEATING_ENABLED_KEY) } returns failure.left()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesDao.getAll() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when HEATING_ENABLED_KEY is not a boolean`() {
        every { cacheDao.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry(aRandomUniqueString()).right()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesDao.getAll() }
        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when fails to fetch all devices`() {
        val failure = Exception("all devices fetch failure!")
        every { devicesDao.getAll() } returns PersistenceFailure(failure).left()

        sut.evaluateHeatingState()

        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when there are no heatable areas`() {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = emptyList()
        )
        every { devicesDao.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto).right()

        sut.evaluateHeatingState()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when the area has no sensors (not heatable)`() {
        val sensorDto = aSensor()
        sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = emptyList(),
            actuators = listOf(actuatorDto)
        )
        every { devicesDao.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto).right()

        sut.evaluateHeatingState()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when the area has misconfigured sensors (not heatable)`() {
        val sensorDto = aSensor()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        every { devicesDao.getAll() } returns listOf(actuatorDto).right() // sensor missing from registry
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto).right()

        sut.evaluateHeatingState()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when the area has misconfigured actuator (not heatable)`() {
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        every { devicesDao.getAll() } returns listOf(sensorDto).right() // heater missing from registry
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto).right()

        sut.evaluateHeatingState()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when both default and selected strategies services are missing`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val strategy = DynamicSharedHeatingStrategyService(ECONOMY, emptyList(), cacheDao)
        val sut = HeatingOrchestrationService(devicesService, areasService, strategy, cacheDao)
        givenHeatableArea()

        sut.evaluateHeatingState()

        verify(exactly = 1) { devicesDao.getAll() }
        verify(exactly = 1) { areasWithDevicesDao.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when the selected strategy is missing`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val strategy = DynamicSharedHeatingStrategyService(ECONOMY, listOf(economyStrategy), cacheDao)
        val sut = HeatingOrchestrationService(devicesService, areasService, strategy, cacheDao)
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on economy strategy`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(ECONOMY.name).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when selected strategy is not valid`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(aRandomUniqueString()).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when there is no selected strategy`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns PersistedCacheEntryNotFound.left()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when fails to fetch selected strategy`() {
        val failure = PersistenceFailure(Exception("cache fetch failure!"))
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns failure.left()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on comfort strategy`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(comfortStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on strategy name ignoring case`() {
        every { cacheDao.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name.lowercase()).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(comfortStrategy)
    }

    private fun givenHeatableArea(): Pair<AreaDtoWithDevices, SharedHeater> {
        val sensorDto = aSensor()
        sensorDto.toSensorMockk()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        every { devicesDao.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto).right()
        return areaDto to heater
    }

    private fun Pair<AreaDtoWithDevices, SharedHeater>.verifyHandledBy(
        strategy: NamedSharedHeatingAreasStrategyService
    ) {
        val heaterSlot = slot<Heater>()
        val areasSlot = slot<Collection<HeatableArea>>()
        coVerify(exactly = 1) { strategy.handleHeatingFor(capture(heaterSlot), capture(areasSlot)) }
        heaterSlot.captured shouldBe second
        areasSlot.captured.map { it.uuid }.shouldContainExactlyInAnyOrder(first.uuid)
    }

    private fun verifyStrategiesNoInteractions() {
        coVerify(exactly = 0) { economyStrategy.handleHeatingFor(any(), any()) }
        coVerify(exactly = 0) { comfortStrategy.handleHeatingFor(any(), any()) }
    }

    private fun DeviceDto.toSensorMockk(): Sensor {
        val dto = this
        val sensor: Sensor = mockk()
        every { sensor.uuid } returns uuid
        every { factory.createDevice(dto) } returns sensor
        return sensor
    }

    private fun DeviceDto.toSharedHeaterMockk(): SharedHeater {
        val dto = this
        val heater: SharedHeater = mockk(relaxed = true)
        every { heater.uuid } returns uuid
        every { factory.createDevice(dto) } returns heater
        return heater
    }

}
