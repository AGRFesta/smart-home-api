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
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.areas.AreasFactory
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.commons.CacheEntry
import org.agrfesta.sh.api.core.domain.devices.DeviceDto
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.agrfesta.sh.api.core.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.UnitOfWork
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TemperatureSettingsRepository
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

    private val devicesRepository: DevicesRepository = mockk()
    private val factory: ProviderDevicesFactory = mockk {
        every { provider } returns Provider.SWITCHBOT
    }
    private val areasRepository: AreasRepository = mockk()
    private val areasWithDevicesRepository: AreasWithDevicesRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val cacheRepository: CacheRepository = mockk()
    private val timeService: TimeService = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val unitOfWork: UnitOfWork = mockk()
    private val economyStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns ECONOMY
    }
    private val comfortStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns COMFORT
    }

    private val devicesService = DevicesService(devicesRepository, randomGenerator, listOf(factory))
    private val heatingAreasService = HeatingAreasService(areasRepository, temperatureSettingsRepository, unitOfWork)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)
    private val areasService = AreasService(areasWithDevicesRepository, areasFactory)
    private val strategy = DynamicSharedHeatingStrategyService(
        ECONOMY, listOf(economyStrategy, comfortStrategy), cacheRepository)

    private val sut = HeatingOrchestrationService(devicesService, areasService, strategy, cacheRepository)

    init {
        every { timeService.now() } returns now
        every { cacheRepository.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry("true").right()
        every { devicesRepository.getAll() } returns emptyList<DeviceDto>().right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<AreaDtoWithDevices>().right()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when is disabled`() {
        every { cacheRepository.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry("false").right()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when HEATING_ENABLED_KEY is missing`() {
        every { cacheRepository.findEntry(HEATING_ENABLED_KEY) } returns null.right()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when fails to fetch HEATING_ENABLED_KEY from persistence`() {
        val failure = PersistenceFailure(Exception("cache fetch failure!"))
        every { cacheRepository.findEntry(HEATING_ENABLED_KEY) } returns failure.left()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when HEATING_ENABLED_KEY is not a boolean`() {
        every { cacheRepository.findEntry(HEATING_ENABLED_KEY) } returns CacheEntry(aRandomUniqueString()).right()

        sut.evaluateHeatingState()

        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when fails to fetch all devices`() {
        val failure = Exception("all devices fetch failure!")
        every { devicesRepository.getAll() } returns PersistenceFailure(failure).left()

        sut.evaluateHeatingState()

        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
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
        every { devicesRepository.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

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
        every { devicesRepository.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

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
        every { devicesRepository.getAll() } returns listOf(actuatorDto).right() // sensor missing from registry
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

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
        every { devicesRepository.getAll() } returns listOf(sensorDto).right() // heater missing from registry
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        sut.evaluateHeatingState()

        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Do nothing when both default and selected strategies services are missing`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val strategy = DynamicSharedHeatingStrategyService(ECONOMY, emptyList(), cacheRepository)
        val sut = HeatingOrchestrationService(devicesService, areasService, strategy, cacheRepository)
        givenHeatableArea()

        sut.evaluateHeatingState()

        verify(exactly = 1) { devicesRepository.getAll() }
        verify(exactly = 1) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verifyStrategiesNoInteractions()
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when the selected strategy is missing`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val strategy = DynamicSharedHeatingStrategyService(ECONOMY, listOf(economyStrategy), cacheRepository)
        val sut = HeatingOrchestrationService(devicesService, areasService, strategy, cacheRepository)
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on economy strategy`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(ECONOMY.name).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when selected strategy is not valid`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(aRandomUniqueString()).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when there is no selected strategy`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns PersistedCacheEntryNotFound.left()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on default strategy when fails to fetch selected strategy`() {
        val failure = PersistenceFailure(Exception("cache fetch failure!"))
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns failure.left()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on comfort strategy`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name).right()
        val testData = givenHeatableArea()

        sut.evaluateHeatingState()

        testData.verifyHandledBy(comfortStrategy)
    }

    @Test
    fun `evaluateHeatingState() Handle heater based on strategy name ignoring case`() {
        every { cacheRepository.getEntry(HEATING_STRATEGY_KEY) } returns CacheEntry(COMFORT.name.lowercase()).right()
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
        every { devicesRepository.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()
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
