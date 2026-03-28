package org.agrfesta.sh.api.schedulers

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.commons.Percentage
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DevicesDataHistorySchedulerIntegrationTest(
    private val devicesDataFetchScheduler: DevicesDataFetchScheduler,
    private val sut: DevicesDataHistoryScheduler,
    private val devicesDao: DevicesDao,
    private val historyDao: SensorsHistoryDataDao,
    private val switchBotClientAsserter: SwitchBotClientAsserter
): AbstractIntegrationTest() {
    private val now = Instant.now()

    @BeforeEach
    fun init() {
        every { timeService.now() } returns now
    }

    @Test fun `historyDevicesData() saves all cached device values`() {
        val sensorData = aRandomThermoHygroData(
            relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity()))
//        val sensorTemperature = aRandomTemperature()
//        val sensorHumidity = aRandomIntHumidity()
        val sensor = aDeviceDataValue(features = setOf(SENSOR))
        val uuid = devicesDao.create(sensor)
        switchBotClientAsserter.givenSensorData(sensor.deviceProviderId, sensorData)
        devicesDataFetchScheduler.fetchDevicesData() // Force to fetch devices data and put them in cache

        sut.historyDevicesData()

        historyDao.findBySensor(uuid).apply {
            map { listOf(it.time.truncatedTo(ChronoUnit.SECONDS), it.type, it.value) }.shouldContainExactlyInAnyOrder(
                listOf(now.truncatedTo(ChronoUnit.SECONDS), TEMPERATURE, sensorData.temperature.value),
                listOf(now.truncatedTo(ChronoUnit.SECONDS), HUMIDITY, sensorData.relativeHumidity.value)
            )
        }
    }

}
