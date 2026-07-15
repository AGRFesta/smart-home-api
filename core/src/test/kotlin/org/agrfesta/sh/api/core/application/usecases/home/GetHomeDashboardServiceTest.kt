package org.agrfesta.sh.api.core.application.usecases.home

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldFailure
import org.agrfesta.sh.api.core.application.readmodels.commons.FieldSuccess
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertTarget
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.average
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DashboardRepositoryError
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupError
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAlert
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.anAreaWithDevicesView
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.UUID

class GetHomeDashboardServiceTest {
    private val propertyRepository: PropertyRepository = mockk()
    private val areasWithDevicesRepository: AreasWithDevicesRepository = mockk()
    private val sensorsCurrentReadingsRepository: SensorsCurrentReadingsRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val timeProvider: TimeProvider = mockk()
    private val alertsRepository: AlertsRepository = mockk()

    private val sut = GetHomeDashboardService(
        propertyRepository,
        areasWithDevicesRepository,
        sensorsCurrentReadingsRepository,
        temperatureSettingsRepository,
        timeProvider,
        alertsRepository
    )

    init {
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns null.right()
        every { propertyRepository.findEntry(HEATING_STRATEGY_KEY) } returns null.right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<Nothing>().right()
        every { temperatureSettingsRepository.findAreaSetting(any()) } returns null.right()
        every { sensorsCurrentReadingsRepository.findBy(any()) } returns null.right()
        every { timeProvider.currentLocalTime() } returns LocalTime.NOON
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Nothing>().right()
    }

    // heatingActive ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() heatingActive is true when heating_enabled property is true`() {
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()

        val result = sut.execute().shouldBeRight()

        withClue("heatingActive") { result.globalState.heatingActive shouldBe FieldSuccess(true) }
    }

    @Test fun `execute() heatingActive is true when heating_enabled property is TRUE (case insensitive)`() {
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("TRUE").right()

        val result = sut.execute().shouldBeRight()

        withClue("heatingActive") { result.globalState.heatingActive shouldBe FieldSuccess(true) }
    }

    @Test fun `execute() heatingActive is false when heating_enabled property is false`() {
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("false").right()

        val result = sut.execute().shouldBeRight()

        withClue("heatingActive") { result.globalState.heatingActive shouldBe FieldSuccess(false) }
    }

    @Test fun `execute() heatingActive is false when heating_enabled property is absent`() {
        val result = sut.execute().shouldBeRight()

        withClue("heatingActive") { result.globalState.heatingActive shouldBe FieldSuccess(false) }
    }

    @Test fun `execute() heatingActive is a field failure when heating_enabled lookup fails`() {
        every {
            propertyRepository.findEntry(HEATING_ENABLED_KEY)
        } returns PropertyRepositoryError.left()

        val result = sut.execute().shouldBeRight()

        withClue("heatingActive") {
            result.globalState.heatingActive shouldBe FieldFailure("Unable to retrieve heating status")
        }
    }

    // strategy ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() strategy is ECONOMY when heating_strategy property is ECONOMY`() {
        every { propertyRepository.findEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry("ECONOMY").right()

        val result = sut.execute().shouldBeRight()

        withClue("strategy") { result.globalState.strategy shouldBe FieldSuccess(SharedHeatingStrategy.ECONOMY) }
    }

    @Test fun `execute() strategy is COMFORT when heating_strategy property is comfort (case insensitive)`() {
        every { propertyRepository.findEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry("comfort").right()

        val result = sut.execute().shouldBeRight()

        withClue("strategy") { result.globalState.strategy shouldBe FieldSuccess(SharedHeatingStrategy.COMFORT) }
    }

    @Test fun `execute() strategy is null when heating_strategy property is absent`() {
        val result = sut.execute().shouldBeRight()

        withClue("strategy") { result.globalState.strategy shouldBe FieldSuccess(null) }
    }

    @Test fun `execute() strategy is null when heating_strategy property has an unrecognized value`() {
        every { propertyRepository.findEntry(HEATING_STRATEGY_KEY) } returns PropertyEntry("UNKNOWN").right()

        val result = sut.execute().shouldBeRight()

        withClue("strategy") { result.globalState.strategy shouldBe FieldSuccess(null) }
    }

    @Test fun `execute() strategy is a field failure when heating_strategy lookup fails`() {
        every {
            propertyRepository.findEntry(HEATING_STRATEGY_KEY)
        } returns PropertyRepositoryError.left()

        val result = sut.execute().shouldBeRight()

        withClue("strategy") {
            result.globalState.strategy shouldBe FieldFailure("Unable to retrieve heating strategy")
        }
    }

    // areas ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() returns Left when areas repository lookup fails`() {
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns AreaRepositoryError.left()

        val result = sut.execute().shouldBeLeft()

        result shouldBe DashboardRepositoryError
    }

    @Test fun `execute() areas is empty when no areas exist`() {
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns emptyList<Nothing>().right()

        val result = sut.execute().shouldBeRight()

        withClue("areas") { result.areas shouldBe emptyList() }
    }

    @Test fun `execute() areas contains id and name for each area`() {
        val area1 = anAreaWithDevicesView()
        val area2 = anAreaWithDevicesView()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area1, area2).right()

        val result = sut.execute().shouldBeRight()

        withClue("areas") {
            result.areas.map { it.id to it.name } shouldContainExactlyInAnyOrder listOf(
                area1.uuid to area1.name,
                area2.uuid to area2.name
            )
        }
    }

    // heating — currentTemperature ////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() area heating currentTemperature is null when area has no sensors`() {
        val area = anAreaWithDevicesView(sensors = emptyList())
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldSuccess(null)
        }
    }

    @Test
    fun `execute() area heating currentTemperature is the sensor temperature when area has one sensor with readings`() {
        val sensor = aSensor()
        val data = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor) } returns data.right()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldSuccess(data.temperature)
        }
    }

    @Test fun `execute() area heating currentTemperature is null when sensor has no cached readings`() {
        val sensor = aSensor()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor) } returns null.right()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldSuccess(null)
        }
    }

    @Test
    fun `execute() area heating currentTemperature is the average when area has multiple sensors with readings`() {
        val sensor1 = aSensor()
        val sensor2 = aSensor()
        val data1 = aRandomThermoHygroData()
        val data2 = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(sensor1, sensor2))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor1) } returns data1.right()
        every { sensorsCurrentReadingsRepository.findBy(sensor2) } returns data2.right()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldSuccess(listOf(data1.temperature, data2.temperature).average())
        }
    }

    @Test
    fun `execute() area heating currentTemperature is average of readings when some sensors have no cached readings`() {
        val sensor1 = aSensor()
        val sensor2 = aSensor()
        val data = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(sensor1, sensor2))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor1) } returns data.right()
        every { sensorsCurrentReadingsRepository.findBy(sensor2) } returns null.right()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldSuccess(data.temperature)
        }
    }

    @Test fun `execute() area heating currentTemperature is a FieldFailure when sensor readings lookup fails`() {
        val sensor = aSensor()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor) } returns
            ReadingsLookupError(Exception("cache error")).left()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldFailure("Unable to retrieve area temperature readings")
        }
    }

    @Test fun `execute() area heating currentTemperature ignores a failed sensor when another sensor succeeds`() {
        val failingSensor = aSensor()
        val workingSensor = aSensor()
        val data = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(failingSensor, workingSensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(failingSensor) } returns
            ReadingsLookupError(Exception("cache error")).left()
        every { sensorsCurrentReadingsRepository.findBy(workingSensor) } returns data.right()

        val result = sut.execute().shouldBeRight()

        withClue("currentTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .currentTemperature shouldBe FieldSuccess(data.temperature)
        }
    }

    // heating — targetTemperature /////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() area heating targetTemperature is null when no temperature setting exists for the area`() {
        val area = anAreaWithDevicesView()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { temperatureSettingsRepository.findAreaSetting(area.uuid) } returns null.right()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()

        val result = sut.execute().shouldBeRight()

        withClue("targetTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .targetTemperature shouldBe FieldSuccess(null)
        }
    }

    @Test
    fun `execute() area heating targetTemperature is the default temperature when no interval matches`() {
        val area = anAreaWithDevicesView()
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = emptySet())
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { temperatureSettingsRepository.findAreaSetting(area.uuid) } returns setting.right()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()
        every { timeProvider.currentLocalTime() } returns LocalTime.of(12, 0)

        val result = sut.execute().shouldBeRight()

        withClue("targetTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .targetTemperature shouldBe FieldSuccess(setting.defaultTemperature)
        }
    }

    @Test fun `execute() area heating targetTemperature is the interval temperature when a matching interval exists`() {
        val area = anAreaWithDevicesView()
        val intervalTemp = aRandomTemperature()
        val interval = aTemperatureInterval(
            temperature = intervalTemp,
            startTime = LocalTime.of(13, 0),
            endTime = LocalTime.of(15, 0)
        )
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = setOf(interval))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { temperatureSettingsRepository.findAreaSetting(area.uuid) } returns setting.right()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()
        every { timeProvider.currentLocalTime() } returns LocalTime.of(14, 0)

        val result = sut.execute().shouldBeRight()

        withClue("targetTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .targetTemperature shouldBe FieldSuccess(intervalTemp)
        }
    }

    @Test fun `execute() area heating targetTemperature is a FieldFailure when temperature settings lookup fails`() {
        val area = anAreaWithDevicesView()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { temperatureSettingsRepository.findAreaSetting(area.uuid) } returns
            HeatingScheduleRepositoryError.left()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("true").right()

        val result = sut.execute().shouldBeRight()

        withClue("targetTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .targetTemperature shouldBe FieldFailure("Unable to retrieve area target temperature")
        }
    }

    @Test fun `execute() area heating targetTemperature is null when heating is disabled`() {
        val area = anAreaWithDevicesView()
        val setting = anAreaTemperatureSetting(areaId = area.uuid)
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { temperatureSettingsRepository.findAreaSetting(area.uuid) } returns setting.right()
        every { propertyRepository.findEntry(HEATING_ENABLED_KEY) } returns PropertyEntry("false").right()

        val result = sut.execute().shouldBeRight()

        withClue("targetTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .targetTemperature shouldBe FieldSuccess(null)
        }
    }

    @Test fun `execute() area heating targetTemperature is null when heating state cannot be determined`() {
        val area = anAreaWithDevicesView()
        val setting = anAreaTemperatureSetting(areaId = area.uuid)
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { temperatureSettingsRepository.findAreaSetting(area.uuid) } returns setting.right()
        every {
            propertyRepository.findEntry(HEATING_ENABLED_KEY)
        } returns PropertyRepositoryError.left()

        val result = sut.execute().shouldBeRight()

        withClue("targetTemperature") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.heating.shouldNotBeNull()
                .targetTemperature shouldBe FieldSuccess(null)
        }
    }

    // humidity — relative /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() area humidity relative is null when area has no sensors`() {
        val area = anAreaWithDevicesView(sensors = emptyList())
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldSuccess(null)
        }
    }

    @Test fun `execute() area humidity relative is the sensor humidity when area has one sensor with readings`() {
        val sensor = aSensor()
        val data = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor) } returns data.right()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldSuccess(data.relativeHumidity.value)
        }
    }

    @Test fun `execute() area humidity relative is null when sensor has no cached readings`() {
        val sensor = aSensor()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor) } returns null.right()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldSuccess(null)
        }
    }

    @Test fun `execute() area humidity relative is the average when area has multiple sensors with readings`() {
        val sensor1 = aSensor()
        val sensor2 = aSensor()
        val data1 = aRandomThermoHygroData()
        val data2 = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(sensor1, sensor2))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor1) } returns data1.right()
        every { sensorsCurrentReadingsRepository.findBy(sensor2) } returns data2.right()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldSuccess(listOf(data1.relativeHumidity, data2.relativeHumidity).average())
        }
    }

    @Test
    fun `execute() area humidity relative is average of readings when some sensors have no cached readings`() {
        val sensor1 = aSensor()
        val sensor2 = aSensor()
        val data = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(sensor1, sensor2))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor1) } returns data.right()
        every { sensorsCurrentReadingsRepository.findBy(sensor2) } returns null.right()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldSuccess(data.relativeHumidity.value)
        }
    }

    @Test fun `execute() area humidity relative is a FieldFailure when sensor readings lookup fails`() {
        val sensor = aSensor()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(sensor) } returns
            ReadingsLookupError(Exception("cache error")).left()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldFailure("Unable to retrieve area humidity readings")
        }
    }

    @Test fun `execute() area humidity relative ignores a failed sensor when another sensor succeeds`() {
        val failingSensor = aSensor()
        val workingSensor = aSensor()
        val data = aRandomThermoHygroData()
        val area = anAreaWithDevicesView(sensors = listOf(failingSensor, workingSensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { sensorsCurrentReadingsRepository.findBy(failingSensor) } returns
            ReadingsLookupError(Exception("cache error")).left()
        every { sensorsCurrentReadingsRepository.findBy(workingSensor) } returns data.right()

        val result = sut.execute().shouldBeRight()

        withClue("relative") {
            result.areas.shouldNotBeEmpty().first()
                .measurements.humidity.shouldNotBeNull()
                .relative shouldBe FieldSuccess(data.relativeHumidity.value)
        }
    }

    // activeAlerts ////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `execute() area activeAlerts is an empty set when there are no open alerts`() {
        val area = anAreaWithDevicesView()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Nothing>().right()

        val result = sut.execute().shouldBeRight()

        withClue("activeAlerts") {
            result.areas.shouldNotBeEmpty().first().activeAlerts shouldBe FieldSuccess(emptySet<AlertType>())
        }
    }

    @Test fun `execute() area activeAlerts contains the alert type when an open alert targets a sensor of the area`() {
        val sensor = aSensor()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns
            listOf(anAlert(type = AlertType.BATTERY_LOW, target = AlertTarget.Device(sensor.uuid))).right()

        val result = sut.execute().shouldBeRight()

        withClue("activeAlerts") {
            result.areas.shouldNotBeEmpty().first().activeAlerts shouldBe FieldSuccess(setOf(AlertType.BATTERY_LOW))
        }
    }

    @Test fun `execute() area activeAlerts excludes open alerts not targeting a device of the area`() {
        val sensor = aSensor()
        val area = anAreaWithDevicesView(sensors = listOf(sensor))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns listOf(
            anAlert(target = AlertTarget.Device(UUID.randomUUID())),
            anAlert(target = AlertTarget.Provider(Provider.SWITCHBOT)),
            anAlert(target = AlertTarget.Global)
        ).right()

        val result = sut.execute().shouldBeRight()

        withClue("activeAlerts") {
            result.areas.shouldNotBeEmpty().first().activeAlerts shouldBe FieldSuccess(emptySet<AlertType>())
        }
    }

    @Test
    fun `execute() area activeAlerts contains the alert type when an open alert targets an actuator of the area`() {
        val actuator = anActuator()
        val area = anAreaWithDevicesView(actuators = listOf(actuator))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area).right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns
            listOf(anAlert(type = AlertType.BATTERY_LOW, target = AlertTarget.Device(actuator.uuid))).right()

        val result = sut.execute().shouldBeRight()

        withClue("activeAlerts") {
            result.areas.shouldNotBeEmpty().first().activeAlerts shouldBe FieldSuccess(setOf(AlertType.BATTERY_LOW))
        }
    }

    @Test fun `execute() area activeAlerts is a FieldFailure on every area when open alerts lookup fails`() {
        val area1 = anAreaWithDevicesView(sensors = listOf(aSensor()))
        val area2 = anAreaWithDevicesView()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area1, area2).right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns AlertRepositoryError.left()

        val result = sut.execute().shouldBeRight()

        withClue("activeAlerts") {
            result.areas.shouldNotBeEmpty().forEach { area ->
                area.activeAlerts shouldBe FieldFailure("Unable to retrieve active alerts")
            }
        }
    }

    @Test fun `execute() reads open alerts once regardless of the number of areas and devices`() {
        val area1 = anAreaWithDevicesView(sensors = listOf(aSensor()))
        val area2 = anAreaWithDevicesView(sensors = listOf(aSensor()), actuators = listOf(anActuator()))
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns listOf(area1, area2).right()
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns emptyList<Nothing>().right()

        sut.execute().shouldBeRight()

        verify(exactly = 1) { alertsRepository.getAlerts(AlertStatus.OPEN) }
    }
}
