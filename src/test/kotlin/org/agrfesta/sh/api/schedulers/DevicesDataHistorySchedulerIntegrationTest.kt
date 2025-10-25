package org.agrfesta.sh.api.schedulers

import com.ninjasquad.springmockk.MockkBean
import com.redis.testcontainers.RedisContainer
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import java.time.Instant
import java.time.temporal.ChronoUnit
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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class DevicesDataHistorySchedulerIntegrationTest(
    @Autowired @MockkBean private val timeService: TimeService,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient,
    @Autowired private val devicesDataFetchScheduler: DevicesDataFetchScheduler,
    @Autowired private val sut: DevicesDataHistoryScheduler,
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val historyDao: SensorsHistoryDataDao,
    @Autowired private val switchBotClientAsserter: SwitchBotClientAsserter
) {
    private val now = Instant.now()

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("timescale/timescaledb:latest-pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }

        @Container
        @ServiceConnection
        val redis = RedisContainer(DockerImageName.parse("redis:7.0.10-alpine"))

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
