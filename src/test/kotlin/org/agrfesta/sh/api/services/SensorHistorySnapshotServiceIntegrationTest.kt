package org.agrfesta.sh.api.services

import arrow.core.getOrElse
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsHistoryDataRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SensorHistorySnapshotServiceIntegrationTest(
    private val sut: SensorHistorySnapshotService,
    private val syncService: SensorReadingsSyncService,
    private val devicesRepository: DevicesJdbcRepository,
    private val historyDao: SensorsHistoryDataRepository,
    private val switchBotClientAsserter: SwitchBotClientAsserter
): AbstractIntegrationTest() {
    private val now = Instant.now()

    @BeforeEach
    fun init() {
        every { timeService.now() } returns now
    }

    @Test fun `snapshotDevicesData() saves all cached sensor values`() {
        val sensorData = aRandomThermoHygroData(
            relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity()))
        val sensor = aProviderDeviceData(features = setOf(SENSOR))
        val uuid = UUID.randomUUID()
        devicesRepository.persist(uuid, sensor)
        switchBotClientAsserter.givenSensorData(sensor.deviceProviderId, sensorData)
        runBlocking { syncService.fetchAndCacheSensorData() }

        sut.snapshotDevicesData()

        historyDao.findBySensor(uuid)
            .getOrElse { error("Failed to fetch sensor history: $it") }
            .map { listOf(it.time.truncatedTo(ChronoUnit.SECONDS), it.type, it.value) }
            .shouldContainExactlyInAnyOrder(
                listOf(now.truncatedTo(ChronoUnit.SECONDS), TEMPERATURE, sensorData.temperature.value),
                listOf(now.truncatedTo(ChronoUnit.SECONDS), HUMIDITY, sensorData.relativeHumidity.value)
            )
    }

}
