package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.FailureByException
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.SharedHeater
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaWithDevicesView
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.ActuatorFailureByException
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AreasUnavailable
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DevicesUnavailable
import org.agrfesta.sh.api.core.domain.failures.HeatingFlagUnavailable
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.ActuationOutcome
import org.agrfesta.sh.api.core.domain.heating.HeatableAreaSnapshot
import org.agrfesta.sh.api.core.domain.heating.HeaterActionOutcome
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.HeatingDecider
import org.agrfesta.sh.api.core.domain.heating.HeatingEvaluationReport
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.COMFORT
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy.ECONOMY
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaWithDevicesView
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

    private val sut = EvaluateHeatingStateService(
        RegisteredDeviceDrivers(devicesRepository, listOf(factory)),
        areasWithDevicesRepository,
        temperatureSettingsRepository,
        strategySelector,
        propertyRepository,
        timeProvider,
        HeatingActuationLock()
    )

    init {
        every { timeProvider.currentLocalTime() } returns LocalTime.now()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()
        every { devicesRepository.getAll() } returns emptyList<Device>().right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<AreaWithDevicesView>().right()
        every { temperatureSettingsRepository.findAreaSetting(any()) } returns null.right()
        every { strategySelector.select() } returns decide
    }

    @Test
    fun `execute() returns Skipped and does not act when heating is disabled`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("false").right()

        // When
        val result = sut.execute()

        // Then
        result shouldBe HeatingEvaluationReport.Skipped.right()
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() returns HeatingFlagUnavailable and does not act when the enabled-flag fetch fails`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        withClue("an unreadable heating.enabled flag must surface as a failure, not as a silent skip") {
            result shouldBe HeatingFlagUnavailable.left()
        }
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() returns Skipped and does not act when HEATING_ENABLED_KEY is missing`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns null.right()

        // When
        val result = sut.execute()

        // Then
        withClue("a missing heating.enabled flag means heating is disabled, not a failure") {
            result shouldBe HeatingEvaluationReport.Skipped.right()
        }
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() returns Skipped and does not act when HEATING_ENABLED_KEY is not a boolean string`() {
        // Given
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry(aRandomUniqueString()).right()

        // When
        val result = sut.execute()

        // Then
        withClue("a non-boolean heating.enabled flag means heating is disabled, not a failure") {
            result shouldBe HeatingEvaluationReport.Skipped.right()
        }
        verify(exactly = 0) { devicesRepository.getAll() }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() returns DevicesUnavailable and does not act when device fetch fails`() {
        // Given
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        withClue("a failed device fetch must surface as a failure, not as a silent skip") {
            result shouldBe DevicesUnavailable.left()
        }
        verify(exactly = 0) { areasWithDevicesRepository.getAllAreasWithDevices() }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() returns AreasUnavailable and does not act when area fetch fails`() {
        // Given
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns AreaRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        withClue("a failed area fetch must surface as a failure, not as a silent skip") {
            result shouldBe AreasUnavailable.left()
        }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() returns Evaluated with no outcomes when there are no heatable areas`() {
        // Given — area with a resolved sensor but no heater: not heatable
        val sensorDto = aSensor()
        sensorDto.toSensorMockk(factory)
        val areaDto = anAreaWithDevicesView(sensors = listOf(sensorDto), actuators = emptyList())
        every { devicesRepository.getAll() } returns listOf(sensorDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        val result = sut.execute()

        // Then
        withClue("no heatable areas is a successful evaluation with an empty report, not a failure") {
            result shouldBe HeatingEvaluationReport.Evaluated(emptyList()).right()
        }
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() does nothing when area actuator is missing from device registry`() {
        // Given — actuator referenced in area DTO but absent from device registry: only the sensor
        //         resolves, no heater → not heatable
        val sensorDto = aSensor()
        sensorDto.toSensorMockk(factory)
        val actuatorDto = anActuator()
        val areaDto = anAreaWithDevicesView(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
        every { devicesRepository.getAll() } returns listOf(sensorDto).right() // actuatorDto absent from registry
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        sut.execute()

        // Then
        verify(exactly = 0) { decide(any()) }
    }

    @Test
    fun `execute() turns OFF the heater of an area whose sensors are missing from the device registry`() {
        // Given — sensor referenced in area DTO but absent from device registry: no sensor resolves,
        //         area has no resolved sensors → not heatable
        val sensorDto = aSensor()
        val (heaterDto, heater) = aStubbedHeater()
        val areaDto = anAreaWithDevicesView(sensors = listOf(sensorDto), actuators = listOf(heaterDto))
        every { devicesRepository.getAll() } returns listOf(heaterDto).right() // sensorDto absent from registry
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        sut.execute()

        // Then
        withClue("a heater whose only area has no resolvable sensor should receive OFF, without a strategy decision") {
            verify(exactly = 1) { heater.off() }
            verify(exactly = 0) { decide(any()) }
        }
    }

    @Test
    fun `execute() turns OFF a heater whose only area has no sensor`() {
        // Given — the sensor left the area: the area is no longer heatable, its heater is still registered
        val (heaterDto, heater) = aStubbedHeater()
        val areaDto = anAreaWithDevicesView(sensors = emptyList(), actuators = listOf(heaterDto))
        every { devicesRepository.getAll() } returns listOf(heaterDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()

        // When
        val result = sut.execute()

        // Then
        withClue("a heater with no heatable area should receive OFF, so it does not stay on") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(HeaterActionOutcome(heater.uuid, HeaterCommand.OFF, ActuationOutcome.Issued))
            ).right()
            verify(exactly = 1) { heater.off() }
        }
    }

    @Test
    fun `execute() turns OFF a registered heater assigned to no area`() {
        // Given
        val (heaterDto, heater) = aStubbedHeater()
        every { devicesRepository.getAll() } returns listOf(heaterDto).right()

        // When
        val result = sut.execute()

        // Then
        withClue("a registered heater outside every area should receive OFF, so it does not stay on") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(HeaterActionOutcome(heater.uuid, HeaterCommand.OFF, ActuationOutcome.Issued))
            ).right()
            verify(exactly = 1) { heater.off() }
        }
    }

    @Test
    fun `execute() reports Issued with the command when the heater actuation succeeds`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.ON
        every { heater.on() } returns Unit.right()

        // When
        val result = sut.execute()

        // Then
        withClue("a successful actuation must be reported per heater with the issued command") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(HeaterActionOutcome(heater.uuid, HeaterCommand.ON, ActuationOutcome.Issued))
            ).right()
        }
    }

    @Test
    fun `execute() reports Failed with the actuation cause when the heater actuation fails`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.ON
        val failure = object : ActuatorOperationFailure {}
        every { heater.on() } returns failure.left()

        // When
        val result = sut.execute()

        // Then
        withClue("a failed actuation is a partial outcome in the report, carrying its cause") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(HeaterActionOutcome(heater.uuid, HeaterCommand.ON, ActuationOutcome.Failed(failure)))
            ).right()
        }
    }

    @Test
    fun `execute() reports Failed with the actuation cause when the heater OFF actuation fails`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.OFF
        val failure = object : ActuatorOperationFailure {}
        every { heater.off() } returns failure.left()

        // When
        val result = sut.execute()

        // Then
        withClue("a failed OFF actuation must be reported as Failed with its cause, exactly like a failed ON") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(HeaterActionOutcome(heater.uuid, HeaterCommand.OFF, ActuationOutcome.Failed(failure)))
            ).right()
        }
    }

    @Test
    fun `execute() reports NotNeeded when the decision is NONE`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.NONE

        // When
        val result = sut.execute()

        // Then
        withClue("a NONE decision issues no command, and the report must say so") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(HeaterActionOutcome(heater.uuid, HeaterCommand.NONE, ActuationOutcome.NotNeeded))
            ).right()
        }
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
        withClue("getActuatorStatus() failure is logged (warn) and degrades to UNDEFINED, not throw") {
            snapshots.captured.single().heaterStatus shouldBe ActuatorStatus.UNDEFINED
        }
    }

    @Test
    fun `execute() averages sensor readings and ignores failing fetches in the snapshot temperature`() {
        // Given — three sensors in one area: 10, failing, 20 -> average over the two successful readings is 15
        val sensorDto1 = aSensor()
        val sensor1 = sensorDto1.toSensorMockk(factory)
        every { sensor1.fetchReadings() } returns aThermoHygroDataValue(temperature = Temperature.of("10")).right()
        val sensorDto2 = aSensor()
        val sensor2 = sensorDto2.toSensorMockk(factory)
        every { sensor2.fetchReadings() } returns FailureByException(RuntimeException("unavailable")).left()
        val sensorDto3 = aSensor()
        val sensor3 = sensorDto3.toSensorMockk(factory)
        every { sensor3.fetchReadings() } returns aThermoHygroDataValue(temperature = Temperature.of("20")).right()
        val (heaterDto, _) = aStubbedHeater()
        val areaDto = anAreaWithDevicesView(
            sensors = listOf(sensorDto1, sensorDto2, sensorDto3),
            actuators = listOf(heaterDto)
        )
        every { devicesRepository.getAll() } returns listOf(sensorDto1, sensorDto2, sensorDto3, heaterDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()
        val snapshots = slot<Collection<HeatableAreaSnapshot>>()
        every { decide(capture(snapshots)) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        withClue("failing fetches are skipped; the average is computed over the successful readings") {
            snapshots.captured.single().currentTemperature shouldBe Temperature.of("15")
        }
    }

    @Test
    fun `execute() sets the snapshot temperature to null when all sensor fetches fail`() {
        // Given — single sensor whose fetch fails (givenHeatableArea default)
        givenHeatableArea()
        val snapshots = slot<Collection<HeatableAreaSnapshot>>()
        every { decide(capture(snapshots)) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        withClue("no readings available -> currentTemperature is null, not an error") {
            snapshots.captured.single().currentTemperature.shouldBeNull()
        }
    }

    @Test
    fun `execute() applies a single command to a heater shared by multiple areas`() {
        // Given — two areas referencing the same shared heater
        val sensor1 = aStubbedSensorDto()
        val sensor2 = aStubbedSensorDto()
        val (heaterDto, heater) = aStubbedHeater()
        val area1 = anAreaWithDevicesView(sensors = listOf(sensor1), actuators = listOf(heaterDto))
        val area2 = anAreaWithDevicesView(sensors = listOf(sensor2), actuators = listOf(heaterDto))
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
        val area1 = anAreaWithDevicesView(sensors = listOf(sensor1), actuators = listOf(heater1Dto))
        val area2 = anAreaWithDevicesView(sensors = listOf(sensor2), actuators = listOf(heater2Dto))
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

    @Test
    fun `execute() turns ON every heater of an area under COMFORT`() {
        // Given — the living room, at 18° with target 21°, is served by two heaters
        val (heater1Dto, heater1) = aStubbedHeater()
        val (heater2Dto, heater2) = aStubbedHeater()
        val (sensorDto, livingRoom) = anAreaServedBy(
            heaterDtos = listOf(heater1Dto, heater2Dto),
            current = Temperature.of("18"),
            target = Temperature.of("21")
        )
        every { devicesRepository.getAll() } returns listOf(sensorDto, heater1Dto, heater2Dto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(livingRoom).right()
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()

        // When
        val result = sutWithRealSelector().execute()

        // Then
        withClue("each heater of an area should receive its own command: both heaters ON") {
            val heaters = result.shouldBeRight().shouldBeInstanceOf<HeatingEvaluationReport.Evaluated>().heaters
            heaters shouldContainExactlyInAnyOrder listOf(
                HeaterActionOutcome(heater1.uuid, HeaterCommand.ON, ActuationOutcome.Issued),
                HeaterActionOutcome(heater2.uuid, HeaterCommand.ON, ActuationOutcome.Issued)
            )
            verify(exactly = 1) { heater1.on() }
            verify(exactly = 1) { heater2.on() }
        }
    }

    @Test
    fun `execute() turns OFF the other heaters without areas when one OFF fails`() {
        // Given
        val (heater1Dto, heater1) = aStubbedHeater()
        val (heater2Dto, heater2) = aStubbedHeater()
        val failure = object : ActuatorOperationFailure {}
        every { heater1.off() } returns failure.left()
        every { devicesRepository.getAll() } returns listOf(heater1Dto, heater2Dto).right()

        // When
        val result = sut.execute()

        // Then
        withClue("a failed OFF should be reported and should not stop the OFF of the other heater") {
            val heaters = result.shouldBeRight().shouldBeInstanceOf<HeatingEvaluationReport.Evaluated>().heaters
            heaters shouldContainExactlyInAnyOrder listOf(
                HeaterActionOutcome(heater1.uuid, HeaterCommand.OFF, ActuationOutcome.Failed(failure)),
                HeaterActionOutcome(heater2.uuid, HeaterCommand.OFF, ActuationOutcome.Issued)
            )
        }
    }

    @Test
    fun `execute() turns ON a heater of a heatable area when the OFF of a heater without areas fails`() {
        // Given
        val (heater1Dto, heater1) = aStubbedHeater()
        val (heater2Dto, heater2) = aStubbedHeater()
        val failure = object : ActuatorOperationFailure {}
        every { heater2.off() } returns failure.left()
        val (sensorDto, livingRoom) =
            anAreaServedBy(listOf(heater1Dto), current = Temperature.of("18"), target = Temperature.of("21"))
        every { devicesRepository.getAll() } returns listOf(sensorDto, heater1Dto, heater2Dto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(livingRoom).right()
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()

        // When
        val result = sutWithRealSelector().execute()

        // Then
        withClue("a failed OFF should be reported and should not stop the ON of the other heater") {
            val heaters = result.shouldBeRight().shouldBeInstanceOf<HeatingEvaluationReport.Evaluated>().heaters
            heaters shouldContainExactlyInAnyOrder listOf(
                HeaterActionOutcome(heater1.uuid, HeaterCommand.ON, ActuationOutcome.Issued),
                HeaterActionOutcome(heater2.uuid, HeaterCommand.OFF, ActuationOutcome.Failed(failure))
            )
        }
    }

    @Test
    fun `execute() turns OFF a heater without areas when the ON of a heater of a heatable area fails`() {
        // Given
        val (heater1Dto, heater1) = aStubbedHeater()
        val (heater2Dto, heater2) = aStubbedHeater()
        val failure = object : ActuatorOperationFailure {}
        every { heater1.on() } returns failure.left()
        val (sensorDto, livingRoom) =
            anAreaServedBy(listOf(heater1Dto), current = Temperature.of("18"), target = Temperature.of("21"))
        every { devicesRepository.getAll() } returns listOf(sensorDto, heater1Dto, heater2Dto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(livingRoom).right()
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()

        // When
        val result = sutWithRealSelector().execute()

        // Then
        withClue("a failed ON should be reported and should not stop the OFF of the other heater") {
            val heaters = result.shouldBeRight().shouldBeInstanceOf<HeatingEvaluationReport.Evaluated>().heaters
            heaters shouldContainExactlyInAnyOrder listOf(
                HeaterActionOutcome(heater1.uuid, HeaterCommand.ON, ActuationOutcome.Failed(failure)),
                HeaterActionOutcome(heater2.uuid, HeaterCommand.OFF, ActuationOutcome.Issued)
            )
        }
    }

    @Test
    fun `execute() reports a failed OFF and turns ON the other heater when the OFF of a heater without areas throws`() {
        // Given
        val (heater1Dto, heater1) = aStubbedHeater()
        val (heater2Dto, heater2) = aStubbedHeater()
        val exception = IllegalStateException("driver broke the port contract")
        every { heater2.off() } throws exception
        val (sensorDto, livingRoom) =
            anAreaServedBy(listOf(heater1Dto), current = Temperature.of("18"), target = Temperature.of("21"))
        every { devicesRepository.getAll() } returns listOf(sensorDto, heater1Dto, heater2Dto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(livingRoom).right()
        every { propertyRepository.getEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry(COMFORT.name).right()

        // When
        val result = sutWithRealSelector().execute()

        // Then
        withClue("an OFF that throws should be reported as failed and should not stop the ON of the other heater") {
            val heaters = result.shouldBeRight().shouldBeInstanceOf<HeatingEvaluationReport.Evaluated>().heaters
            heaters shouldContainExactlyInAnyOrder listOf(
                HeaterActionOutcome(heater1.uuid, HeaterCommand.ON, ActuationOutcome.Issued),
                HeaterActionOutcome(
                    heater2.uuid,
                    HeaterCommand.OFF,
                    ActuationOutcome.Failed(ActuatorFailureByException(exception))
                )
            )
        }
    }

    @Test
    fun `execute() reports a failed ON when the ON of a heater throws`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.ON
        val exception = IllegalStateException("driver broke the port contract")
        every { heater.on() } throws exception

        // When
        val result = sut.execute()

        // Then
        withClue("an ON that throws should be reported as a failed ON, carrying the exception") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(
                    HeaterActionOutcome(
                        heater.uuid,
                        HeaterCommand.ON,
                        ActuationOutcome.Failed(ActuatorFailureByException(exception))
                    )
                )
            ).right()
        }
    }

    @Test
    fun `execute() reports a failed OFF when the OFF decided for a heater throws`() {
        // Given
        val heater = givenHeatableArea().heater
        every { decide(any()) } returns HeaterCommand.OFF
        val exception = IllegalStateException("driver broke the port contract")
        every { heater.off() } throws exception

        // When
        val result = sut.execute()

        // Then
        withClue("an OFF decided by the strategy that throws should be reported as a failed OFF") {
            result shouldBe HeatingEvaluationReport.Evaluated(
                listOf(
                    HeaterActionOutcome(
                        heater.uuid,
                        HeaterCommand.OFF,
                        ActuationOutcome.Failed(ActuatorFailureByException(exception))
                    )
                )
            ).right()
        }
    }

    @Test
    fun `execute() decides with an UNDEFINED heater status when the status fetch of the heater throws`() {
        // Given
        val heater = givenHeatableArea().heater
        every { heater.getActuatorStatus() } throws IllegalStateException("driver broke the port contract")
        val snapshots = slot<Collection<HeatableAreaSnapshot>>()
        every { decide(capture(snapshots)) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        withClue("a status fetch that throws should degrade to UNDEFINED, as a failed fetch does") {
            snapshots.captured.single().heaterStatus shouldBe ActuatorStatus.UNDEFINED
        }
    }

    @Test
    fun `execute() leaves out of the area temperature the reading of a sensor whose fetch throws`() {
        // Given — two sensors in one area: 10 and throwing -> the area temperature is 10
        val sensorDto1 = aSensor()
        val sensor1 = sensorDto1.toSensorMockk(factory)
        every { sensor1.fetchReadings() } returns aThermoHygroDataValue(temperature = Temperature.of("10")).right()
        val sensorDto2 = aSensor()
        val sensor2 = sensorDto2.toSensorMockk(factory)
        every { sensor2.fetchReadings() } throws IllegalStateException("driver broke the port contract")
        val (heaterDto, _) = aStubbedHeater()
        val areaDto = anAreaWithDevicesView(sensors = listOf(sensorDto1, sensorDto2), actuators = listOf(heaterDto))
        every { devicesRepository.getAll() } returns listOf(sensorDto1, sensorDto2, heaterDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(areaDto).right()
        val snapshots = slot<Collection<HeatableAreaSnapshot>>()
        every { decide(capture(snapshots)) } returns HeaterCommand.NONE

        // When
        sut.execute()

        // Then
        withClue("a fetch that throws should be skipped, as a failed fetch is") {
            snapshots.captured.single().currentTemperature shouldBe Temperature.of("10")
        }
    }

    private fun sutWithRealSelector(default: SharedHeatingStrategy = ECONOMY) =
        EvaluateHeatingStateService(
            RegisteredDeviceDrivers(devicesRepository, listOf(factory)),
            areasWithDevicesRepository,
            temperatureSettingsRepository,
            HeatingStrategySelector(default, Percentage.of("0.5"), propertyRepository),
            propertyRepository,
            timeProvider,
            HeatingActuationLock()
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
    ): Pair<Device, AreaWithDevicesView> = anAreaServedBy(listOf(heaterDto), current, target)

    /** One area with a single sensor reading [current] and a [target] setting, served by [heaterDtos]. */
    private fun anAreaServedBy(
        heaterDtos: List<Device>,
        current: Temperature,
        target: Temperature
    ): Pair<Device, AreaWithDevicesView> {
        val sensorDto = aSensor()
        val sensor = sensorDto.toSensorMockk(factory)
        every { sensor.fetchReadings() } returns aThermoHygroDataValue(temperature = current).right()
        val areaDto = anAreaWithDevicesView(sensors = listOf(sensorDto), actuators = heaterDtos)
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
        val areaDto = anAreaWithDevicesView(sensors = listOf(sensorDto), actuators = listOf(actuatorDto))
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
