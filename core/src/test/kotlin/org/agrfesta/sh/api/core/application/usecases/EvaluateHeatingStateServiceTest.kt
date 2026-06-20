package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.core.application.areas.AreasFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.usecases.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.toSensorMockk
import org.agrfesta.sh.api.core.application.usecases.heating.toSharedHeaterMockk
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.FailureByException
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.HeatableAreaSnapshot
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.HeatingDecider
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.aThermoHygroDataValue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.UUID

class EvaluateHeatingStateServiceTest {

    private val devicesRepository: DevicesRepository = mockk()
    private val factory: ProviderDevicesFactory = mockk {
        every { provider } returns Provider.SWITCHBOT
    }
    private val areasWithDevicesRepository: AreasWithDevicesRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val propertyRepository: PropertyRepository = mockk()
    private val timeProvider: TimeProvider = mockk()
    private val strategySelector: HeatingStrategySelector = mockk()
    private val decide: HeatingDecider = mockk()

    private val areasFactory = AreasFactory(temperatureSettingsRepository)

    private val sut = EvaluateHeatingStateService(
        devicesRepository,
        listOf(factory),
        areasWithDevicesRepository,
        areasFactory,
        strategySelector,
        propertyRepository,
        timeProvider
    )

    init {
        every { timeProvider.currentLocalTime() } returns LocalTime.now()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()
        every { devicesRepository.getAll() } returns emptyList<Device>().right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<AreaDtoWithDevices>().right()
        every { temperatureSettingsRepository.findAreaSetting(any()) } returns null.right()
        every { strategySelector.select() } returns decide
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
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() does nothing when HEATING_ENABLED_KEY fetch fails`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyRepositoryError.left()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
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
        verify(exactly = 0) { decide(any()) }
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
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() does nothing when device fetch fails`() {
        // Given
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() does nothing when area fetch fails`() {
        // Given
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns AreaRepositoryError.left()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { decide(any()) }
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
        verify(exactly = 0) { decide(any()) }
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
        verify(exactly = 0) { decide(any()) }
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
        verify(exactly = 0) { decide(any()) }
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
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() turns the heater ON when the decision is ON`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.ON

        // When
        sut.execute()

        // Then
        verify(exactly = 1) { heater.on() }
        verify(exactly = 0) { heater.off() }
    }

    @Test
    fun `execute() turns the heater OFF when the decision is OFF`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.OFF

        // When
        sut.execute()

        // Then
        verify(exactly = 1) { heater.off() }
        verify(exactly = 0) { heater.on() }
    }

    @Test
    fun `execute() does not actuate the heater when the decision is NONE`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { heater.on() }
        verify(exactly = 0) { heater.off() }
    }

    @Test
    fun `execute() assembles the snapshot from sensor reading, area setting and heater status`() {
        // Given
        val currentTemperature = Temperature.of("18.5")
        val targetTemperature = Temperature.of("21")
        val fixture = givenHeatableArea(
            currentTemperature = currentTemperature,
            targetTemperature = targetTemperature,
            heaterStatus = ActuatorStatus.ON.right()
        )
        val snapshots = slot<Collection<HeatableAreaSnapshot>>()
        every { decide(capture(snapshots)) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        val snapshot = snapshots.captured.single()
        withClue("sensor reading -> currentTemperature; setting -> targetTemperature; status -> heaterStatus") {
            snapshot.areaId shouldBe fixture.areaId
            snapshot.currentTemperature shouldBe currentTemperature
            snapshot.targetTemperature shouldBe targetTemperature
            snapshot.heaterStatus shouldBe ActuatorStatus.ON
        }
    }

    @Test
    fun `execute() maps a heater status fetch failure to UNDEFINED in the snapshot`() {
        // Given
        givenHeatableArea(heaterStatus = (object : ActuatorOperationFailure {}).left())
        val snapshots = slot<Collection<HeatableAreaSnapshot>>()
        every { decide(capture(snapshots)) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        withClue("getActuatorStatus() failure must degrade to UNDEFINED, not throw") {
            snapshots.captured.single().heaterStatus shouldBe ActuatorStatus.UNDEFINED
        }
    }

    @Test
    fun `execute() applies a single command to a heater shared by multiple areas`() {
        // Given — two areas referencing the same shared heater
        val sensor1 = aStubbedSensorDto()
        val sensor2 = aStubbedSensorDto()
        val (heaterDto, heater) = aStubbedHeater()
        val area1 = anAreaDtoWithDevices(sensors = listOf(sensor1), actuators = listOf(heaterDto))
        val area2 = anAreaDtoWithDevices(sensors = listOf(sensor2), actuators = listOf(heaterDto))
        every { devicesRepository.getAll() } returns listOf(sensor1, sensor2, heaterDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area1, area2).right()
        every { decide(any()) } returns HeaterCommand.ON

        // When
        sut.execute()

        // Then
        withClue("areas sharing one heater form a single group -> one decision, one command") {
            verify(exactly = 1) { decide(any()) }
            verify(exactly = 1) { heater.on() }
        }
    }

    @Test
    fun `execute() commands each heater once when areas use different heaters`() {
        // Given — two areas, each with its own heater
        val sensor1 = aStubbedSensorDto()
        val sensor2 = aStubbedSensorDto()
        val (heater1Dto, heater1) = aStubbedHeater()
        val (heater2Dto, heater2) = aStubbedHeater()
        val area1 = anAreaDtoWithDevices(sensors = listOf(sensor1), actuators = listOf(heater1Dto))
        val area2 = anAreaDtoWithDevices(sensors = listOf(sensor2), actuators = listOf(heater2Dto))
        every { devicesRepository.getAll() } returns listOf(sensor1, sensor2, heater1Dto, heater2Dto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area1, area2).right()
        every { decide(any()) } returns HeaterCommand.ON

        // When
        sut.execute()

        // Then
        withClue("distinct heaters form distinct groups -> one decision and one command per heater") {
            verify(exactly = 2) { decide(any()) }
            verify(exactly = 1) { heater1.on() }
            verify(exactly = 1) { heater2.on() }
        }
    }

    @Test
    fun `execute() with the real selector turns the shared heater ON under the configured COMFORT strategy`() {
        // Given — 3 areas sharing one heater: 1 below range (demands), 2 in-band with heater OFF (no demand)
        val (heaterDto, heater) = aStubbedHeater()
        givenDiscriminatingAreas(heaterDto)
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()

        // When
        sutWithRealSelector().execute()

        // Then
        withClue("COMFORT: at least one area demands heat -> heater ON") {
            verify(exactly = 1) { heater.on() }
            verify(exactly = 0) { heater.off() }
        }
    }

    @Test
    fun `execute() with the real selector turns the shared heater OFF under the configured ECONOMY strategy`() {
        // Given — same discriminating set: demand ratio 1/3 < threshold 0.5
        val (heaterDto, heater) = aStubbedHeater()
        givenDiscriminatingAreas(heaterDto)
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(ECONOMY.name).right()

        // When
        sutWithRealSelector().execute()

        // Then
        withClue("ECONOMY: demand ratio 1/3 < threshold 0.5 -> heater OFF") {
            verify(exactly = 1) { heater.off() }
            verify(exactly = 0) { heater.on() }
        }
    }

    @Test
    fun `execute() with the real selector falls back to the default strategy when no strategy is configured`() {
        // Given — default is ECONOMY; missing strategy entry must fall back to it (-> OFF on this set)
        val (heaterDto, heater) = aStubbedHeater()
        givenDiscriminatingAreas(heaterDto)
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyNotFound.left()

        // When
        sutWithRealSelector(default = ECONOMY).execute()

        // Then
        withClue("missing strategy -> default ECONOMY -> heater OFF") {
            verify(exactly = 1) { heater.off() }
            verify(exactly = 0) { heater.on() }
        }
    }

    private fun sutWithRealSelector(default: SharedHeatingStrategy = ECONOMY) =
        EvaluateHeatingStateService(
            devicesRepository,
            listOf(factory),
            areasWithDevicesRepository,
            areasFactory,
            HeatingStrategySelector(default, Percentage.of("0.5"), propertyRepository),
            propertyRepository,
            timeProvider
        )

    /**
     * Wires three areas that all share [heaterDto]: one below its target range (demands heat) and two
     * in-band with the heater OFF (no demand, none above range). COMFORT -> ON, ECONOMY (0.5) -> OFF.
     */
    private fun givenDiscriminatingAreas(heaterDto: Device) {
        val below = anAreaSharing(heaterDto, current = Temperature.of("15"), target = Temperature.of("20"))
        val inBandA = anAreaSharing(heaterDto, current = Temperature.of("20.5"), target = Temperature.of("20"))
        val inBandB = anAreaSharing(heaterDto, current = Temperature.of("20.5"), target = Temperature.of("20"))
        every { devicesRepository.getAll() } returns
            listOf(heaterDto, below.first, inBandA.first, inBandB.first).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns
            listOf(below.second, inBandA.second, inBandB.second).right()
    }

    private fun anAreaSharing(
        heaterDto: Device,
        current: Temperature,
        target: Temperature
    ): Pair<Device, AreaDtoWithDevices> {
        val sensorDto = aSensor()
        val sensor = sensorDto.toSensorMockk(factory)
        every { sensor.fetchReadings() } returns aThermoHygroDataValue(temperature = current).right()
        val areaDto = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = listOf(heaterDto))
        every { temperatureSettingsRepository.findAreaSetting(areaDto.uuid) } returns
            AreaTemperatureSetting(areaDto.uuid, target, emptySet()).right()
        return sensorDto to areaDto
    }

    private fun aStubbedSensorDto(): Device {
        val sensorDto = aSensor()
        val sensor = sensorDto.toSensorMockk(factory)
        every { sensor.fetchReadings() } returns FailureByException(RuntimeException("unavailable")).left()
        return sensorDto
    }

    private fun aStubbedHeater(): Pair<Device, SharedHeater> {
        val actuatorDto = anActuator()
        val heater = actuatorDto.toSharedHeaterMockk(factory)
        every { heater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        return actuatorDto to heater
    }

    /**
     * Builds a single heatable area (one sensor + one shared heater) wired into the device and area
     * repositories, returning its area id and heater. By default the sensor reading is unavailable and
     * no temperature setting is configured, so snapshot assembly never throws while leaving the snapshot
     * fields irrelevant when the [decide] decision is mocked. Pass values to control specific fields.
     */
    private fun givenHeatableArea(
        currentTemperature: Temperature? = null,
        targetTemperature: Temperature? = null,
        heaterStatus: Either<ActuatorOperationFailure, ActuatorStatus> = ActuatorStatus.OFF.right()
    ): HeatableAreaFixture {
        val sensorDto = aSensor()
        val sensor = sensorDto.toSensorMockk(factory)
        every { sensor.fetchReadings() } returns (
            currentTemperature?.let { aThermoHygroDataValue(temperature = it).right() }
                ?: FailureByException(RuntimeException("unavailable")).left()
            )
        val actuatorDto = anActuator()
        val heater = actuatorDto.toSharedHeaterMockk(factory)
        every { heater.getActuatorStatus() } returns heaterStatus
        val areaDto = anAreaDtoWithDevices(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
        targetTemperature?.let {
            every { temperatureSettingsRepository.findAreaSetting(areaDto.uuid) } returns
                AreaTemperatureSetting(areaDto.uuid, it, emptySet()).right()
        }
        every { devicesRepository.getAll() } returns listOf(sensorDto, actuatorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()
        return HeatableAreaFixture(areaDto.uuid, heater)
    }

    private data class HeatableAreaFixture(val areaId: UUID, val heater: SharedHeater)
}
