package org.agrfesta.sh.api.core.application.usecases

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
import org.agrfesta.sh.api.core.application.ports.outbounds.UnitOfWork
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.usecases.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.areas.AreasFactory
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.services.heating.NamedSharedHeatingAreasStrategyService
import org.agrfesta.sh.api.services.heating.toSensorMockk
import org.agrfesta.sh.api.services.heating.toSharedHeaterMockk
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategyService
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class EvaluateHeatingStateServiceTest {
    private val now: Instant = Instant.now()

    private val devicesRepository: DevicesRepository = mockk()
    private val factory: ProviderDevicesFactory = mockk {
        every { provider } returns Provider.SWITCHBOT
    }
    private val areasRepository: AreasRepository = mockk()
    private val areasWithDevicesRepository: AreasWithDevicesRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val propertyRepository: PropertyRepository = mockk()
    private val timeService: TimeService = mockk()
    private val unitOfWork: UnitOfWork = mockk()
    private val strategy: SharedHeatingAreasStrategyService = mockk(relaxed = true)
    private val economyStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns ECONOMY
    }
    private val comfortStrategy: NamedSharedHeatingAreasStrategyService = mockk(relaxed = true) {
        every { strategy } returns COMFORT
    }

    private val devicesService = DevicesService(devicesRepository, listOf(factory))
    private val heatingAreasService = HeatingAreasService(areasRepository, temperatureSettingsRepository, unitOfWork)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)
    private val areasService = AreasService(areasWithDevicesRepository, areasFactory)

    private val sut = EvaluateHeatingStateService(devicesService, areasService, strategy, propertyRepository)

    init {
        every { timeService.now() } returns now
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()
        every { devicesRepository.getAll() } returns emptyList<Device>().right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<AreaDtoWithDevices>().right()
    }

    @Test
    fun `execute() does nothing when heating is disabled`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("false").right()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when HEATING_ENABLED_KEY fetch fails`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PersistenceFailure(Exception("cache error")).left()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when HEATING_ENABLED_KEY is missing`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns null.right()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when HEATING_ENABLED_KEY is not a boolean string`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry(aRandomUniqueString()).right()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when device fetch fails`() {
        // Given
        every { devicesRepository.getAll() } returns PersistenceFailure(Exception("db failure")).left()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when area fetch fails`() {
        // Given
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns PersistenceFailure(Exception("db failure")).left()

        // When
        sut.execute()

        // Then
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when there are no heatable areas`() {
        // Given — area with sensors but no actuators: resolves to MonitoredClimateArea, not HeatableArea
        val sensorDto = aSensor()
        sensorDto.toSensorMockk(factory)
        val areaDto = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = emptyList())
        every { devicesRepository.getAll() } returns listOf(sensorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        sut.execute()

        // Then
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when both default and selected strategy services are missing`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()
        val emptyStrategy = DynamicSharedHeatingStrategyService(ECONOMY, emptyList(), propertyRepository)
        val sut = EvaluateHeatingStateService(devicesService, areasService, emptyStrategy, propertyRepository)
        givenHeatableArea()

        // When
        sut.execute()

        // Then
        coVerify(exactly = 0) { economyStrategy.handleHeatingFor(any(), any()) }
        coVerify(exactly = 0) { comfortStrategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when area actuator is missing from device registry`() {
        // Given — actuator referenced in area DTO but absent from device registry: AreasFactory resolves
        //         only the sensor → MonitoredClimateArea, not HeatableArea
        val sensorDto = aSensor()
        sensorDto.toSensorMockk(factory)
        val actuatorDto = anActuator()
        val areaDto = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
        every { devicesRepository.getAll() } returns listOf(sensorDto).right() // actuatorDto absent from registry
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        sut.execute()

        // Then
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when area sensors are missing from device registry`() {
        // Given — sensor referenced in area DTO but absent from device registry: AreasFactory skips it,
        //         area has no resolved sensors → plain AreaImpl, not HeatableArea
        val sensorDto = aSensor()
        val actuatorDto = anActuator()
        actuatorDto.toSharedHeaterMockk(factory)
        val areaDto = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
        every { devicesRepository.getAll() } returns listOf(actuatorDto).right() // sensorDto absent from registry
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        sut.execute()

        // Then
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() does nothing when area has no sensors (not heatable)`() {
        // Given — area with actuator but no sensors: resolves to plain AreaImpl, not HeatableArea
        val actuatorDto = anActuator()
        actuatorDto.toSharedHeaterMockk(factory)
        val areaDto = anAreaDtoWithDevices(sensors = emptyList(), actuators = listOf(actuatorDto))
        every { devicesRepository.getAll() } returns listOf(actuatorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        sut.execute()

        // Then
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `execute() handles heater using economy strategy`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(ECONOMY.name).right()
        val sut = sutWithBothStrategies()
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `execute() handles heater using comfort strategy`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()
        val sut = sutWithBothStrategies()
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(comfortStrategy)
    }

    @Test
    fun `execute() handles heater using strategy name ignoring case`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name.lowercase()).right()
        val sut = sutWithBothStrategies()
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(comfortStrategy)
    }

    @Test
    fun `execute() handles heater using default strategy when selected strategy value is invalid`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(aRandomUniqueString()).right()
        val sut = sutWithBothStrategies()
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `execute() handles heater using default strategy when no selected strategy entry exists`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyNotFound.left()
        val sut = sutWithBothStrategies()
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `execute() handles heater using default strategy when strategy fetch fails`() {
        // Given
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PersistenceFailure(Exception("cache failure")).left()
        val sut = sutWithBothStrategies()
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(economyStrategy)
    }

    @Test
    fun `execute() handles heater using default strategy when selected strategy is missing`() {
        // Given — COMFORT selected but only ECONOMY registered: falls back to default (ECONOMY)
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()
        val sut = sutWith(DynamicSharedHeatingStrategyService(ECONOMY, listOf(economyStrategy), propertyRepository))
        val testData = givenHeatableArea()

        // When
        sut.execute()

        // Then
        testData.verifyHandledBy(economyStrategy)
    }

    private fun sutWith(strategy: SharedHeatingAreasStrategyService) =
        EvaluateHeatingStateService(devicesService, areasService, strategy, propertyRepository)

    private fun sutWithBothStrategies() =
        sutWith(DynamicSharedHeatingStrategyService(ECONOMY, listOf(economyStrategy, comfortStrategy), propertyRepository))

    private fun givenHeatableArea(): Pair<AreaDtoWithDevices, SharedHeater> {
        val sensorDto = aSensor()
        sensorDto.toSensorMockk(factory)
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk(factory)
        val areaDto = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
        every { devicesRepository.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()
        return areaDto to heater
    }

    private fun Pair<AreaDtoWithDevices, SharedHeater>.verifyHandledBy(
        namedStrategy: NamedSharedHeatingAreasStrategyService
    ) {
        val heaterSlot = slot<Heater>()
        val areasSlot = slot<Collection<HeatableArea>>()
        coVerify(exactly = 1) { namedStrategy.handleHeatingFor(capture(heaterSlot), capture(areasSlot)) }
        heaterSlot.captured shouldBe second
        areasSlot.captured.map { it.uuid }.shouldContainExactlyInAnyOrder(first.uuid)
    }

}
