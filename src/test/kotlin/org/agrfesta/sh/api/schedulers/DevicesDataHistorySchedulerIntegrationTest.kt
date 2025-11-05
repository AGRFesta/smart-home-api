package org.agrfesta.sh.api.schedulers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.agrfesta.sh.api.controllers.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.commons.PercentageHundreds
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class DevicesDataHistorySchedulerIntegrationTest(
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient,
    @Autowired private val devicesDataFetchScheduler: DevicesDataFetchScheduler,
    @Autowired private val sut: DevicesDataHistoryScheduler,
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val historyDao: SensorsHistoryDataDao,
    @Autowired private val switchBotClientAsserter: SwitchBotClientAsserter
): AbstractIntegrationTest() {
    private val now = Instant.now()

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()

        @Container
        @ServiceConnection
        val redis = createRedisContainer()
    }

    init {
        every { timeService.now() } returns now
    }

    @Test fun `historyDevicesData() saves all cached device values`() {
        val sensorData = aRandomThermoHygroData(
            relativeHumidity = PercentageHundreds(aRandomIntHumidity()).toPercentage())
//        val sensorTemperature = aRandomTemperature()
//        val sensorHumidity = aRandomIntHumidity()
        val sensor = aDeviceDataValue(features = setOf(SENSOR))
        val uuid = devicesDao.create(sensor)
        switchBotClientAsserter.givenSensorData(sensor.deviceProviderId, sensorData)
        devicesDataFetchScheduler.fetchDevicesData() // Force to fetch devices data and put them in cache

        sut.historyDevicesData()

        historyDao.findBySensor(uuid).apply {
            map { listOf(it.time.truncatedTo(ChronoUnit.SECONDS), it.type, it.value) }.shouldContainExactlyInAnyOrder(
                listOf(now.truncatedTo(ChronoUnit.SECONDS), TEMPERATURE, sensorData.temperature),
                listOf(now.truncatedTo(ChronoUnit.SECONDS), HUMIDITY, sensorData.relativeHumidity.value)
            )
        }
    }

}
