package org.agrfesta.sh.api.schedulers

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.areas.AreasFactory
import org.agrfesta.sh.api.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SharedHeater
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.schedulers.HeatingControlScheduler.Companion.HYSTERESIS
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.services.heating.SharedHeatingAreasStrategy
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.sh.api.utils.toHomeDataTime
import org.agrfesta.test.mothers.aThermoHygroDataValue
import org.junit.jupiter.api.Test

class HeatingControlSchedulerUnitTest {
    private val now: Instant = Instant.now()

    private val devicesService: DevicesService = mockk()
    private val areasDao: AreaDao = mockk()
    private val areasWithDevicesDao: AreasWithDevicesDao = mockk()
    private val temperatureSettingsDao: TemperatureSettingsDao = mockk()
    private val timeService: TimeService = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val strategy: SharedHeatingAreasStrategy = mockk(relaxed = true)

    private val heatingAreasService = HeatingAreasService(areasDao, temperatureSettingsDao)
    private val areasFactory = AreasFactory(heatingAreasService, timeService)
    private val areasService = AreasService(areasDao, areasWithDevicesDao, randomGenerator, areasFactory)
    private val sut = HeatingControlScheduler(devicesService, areasService, strategy)

    init {
        // Default behaviour
        every { timeService.now() } returns now
    }

    @Test
    fun `scheduledTask() Do nothing when fails to fetch all devices`() {
        val failure = Exception("all devices fetch failure!")
        every { devicesService.getAllDevices() } returns PersistenceFailure(failure).left()

        sut.scheduledTask()

        verify(exactly = 0) { areasWithDevicesDao.getAllAreasWithDevices() }
        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
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

        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
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

        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
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

        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
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

        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
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

        coVerify(exactly = 0) { strategy.handleHeatingFor(any(), any()) }
    }

    @Test
    fun `scheduledTask() Handle heater based on strategy`() {
        val areaTHD = aThermoHygroDataValue()
        val sensorDto = aSensor()
        val sensor: Sensor = sensorDto.toSensorMockk()
        coEvery { sensor.fetchReadings() } returns areaTHD.right()
        val actuatorDto = anActuator()
        val heater: SharedHeater = actuatorDto.toSharedHeaterMockk()
        coEvery { heater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        val areaDto = anAreaDtoWithDevices(
            sensors = listOf(sensorDto),
            actuators = listOf(actuatorDto)
        )
        val tempSettings = anAreaTemperatureSetting(
            areaId =  areaDto.uuid,
            temperatureSchedule = setOf(
                aTemperatureInterval(
                    temperature = areaTHD.thermoHygroData.temperature.minus(HYSTERESIS.divide(BigDecimal.TWO)),
                    startTime = now.minusSeconds(3600).toHomeDataTime(),
                    endTime = now.plusSeconds(3600).toHomeDataTime()
                )
            )
        )
        every { devicesService.getAllDevices() } returns listOf(sensor, heater).right()
        every { areasWithDevicesDao.getAllAreasWithDevices() } returns listOf(areaDto)
        every { temperatureSettingsDao.findAreaSetting(areaDto.uuid) } returns tempSettings

        sut.scheduledTask()

        coVerify(exactly = 1) { strategy.handleHeatingFor(heater, any()) }
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
