package org.agrfesta.sh.api.core.application.usecases

import arrow.core.getOrElse
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.core.application.ports.inbounds.FetchSensorReadingsUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.SnapshotSensorHistoryUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsHistoryDataRepository
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SnapshotSensorHistoryServiceIntegrationTest(
    private val sut: SnapshotSensorHistoryUseCase,
    private val fetchSensorReadings: FetchSensorReadingsUseCase,
    private val devicesRepository: DevicesJdbcRepository,
    private val historyDao: SensorsHistoryDataRepository,
    private val switchBotClientAsserter: SwitchBotClientAsserter
): AbstractIntegrationTest() {
    private val now = Instant.now()

    @BeforeEach
    fun init() {
        every { timeService.now() } returns now
    }

    @Test fun `execute() saves all cached sensor values as history records`() {
        val sensorData = aRandomThermoHygroData(
            relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity()))
        val sensor = aProviderDeviceData(features = setOf(SENSOR))
        val uuid = UUID.randomUUID()
        devicesRepository.persist(uuid, sensor)
        switchBotClientAsserter.givenSensorData(sensor.deviceProviderId, sensorData)
        fetchSensorReadings.execute()

        sut.execute()

        historyDao.findBySensor(uuid)
            .getOrElse { error("Failed to fetch sensor history: $it") }
            .map { listOf(it.time.truncatedTo(ChronoUnit.SECONDS), it.type, it.value) }
            .shouldContainExactlyInAnyOrder(
                listOf(now.truncatedTo(ChronoUnit.SECONDS), TEMPERATURE, sensorData.temperature.value),
                listOf(now.truncatedTo(ChronoUnit.SECONDS), HUMIDITY, sensorData.relativeHumidity.value)
            )
    }

}
