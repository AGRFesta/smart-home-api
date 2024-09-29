package org.agrfesta.sh.api.schedulers

import com.ninjasquad.springmockk.MockkBean
import com.redis.testcontainers.RedisContainer
import io.kotest.assertions.arrow.core.shouldBeRight
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.commons.PercentageHundreds
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomTemperature
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
class DevicesDataFetchSchedulerIntegrationTest(
    @Autowired private val sut: DevicesDataFetchScheduler,
    @Autowired private val devicesRepository: DevicesJdbcRepository,
    @Autowired private val cache: Cache,
    @Autowired private val switchBotClientAsserter: SwitchBotClientAsserter,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient
) {

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("timescale/timescaledb:latest-pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }

        @Container
        @ServiceConnection
        val redis = RedisContainer(DockerImageName.parse("redis:7.0.10"))

    }

    @Test fun `fetchDevicesData() caches SwitchBot sensors device values only and ignores failures`() {
        val sensorTemperature = aRandomTemperature()
        val sensorHumidity = aRandomIntHumidity()
        val sensorAndMoreTemperature = aRandomTemperature()
        val sensorAndMoreHumidity = aRandomIntHumidity()
        val sensor = aDeviceDataValue(provider = Provider.SWITCHBOT, features = setOf(SENSOR))
        devicesRepository.persist(sensor)
        val sensorAndMore = aDeviceDataValue(features = setOf(SENSOR, ACTUATOR))
        devicesRepository.persist(sensorAndMore)
        val faultySensor = aDeviceDataValue(features = setOf(SENSOR))
        devicesRepository.persist(faultySensor)
        val device = aDeviceDataValue(features = emptySet())
        devicesRepository.persist(device)
        switchBotClientAsserter.givenSensorData(sensor.providerId, sensorTemperature, sensorHumidity)
        switchBotClientAsserter.givenSensorData(sensorAndMore.providerId, sensorAndMoreTemperature, sensorAndMoreHumidity)
        switchBotClientAsserter.givenSensorDataFailure(faultySensor.providerId)

        sut.fetchDevicesData()

        cache.get("sensors:switchbot:${sensor.providerId}:temperature") shouldBeRight sensorTemperature.toString()
        val expectedSensorHumidity = PercentageHundreds(sensorHumidity).toPercentage().asText()
        cache.get("sensors:switchbot:${sensor.providerId}:humidity") shouldBeRight expectedSensorHumidity
        cache.get("sensors:switchbot:${sensorAndMore.providerId}:temperature") shouldBeRight sensorAndMoreTemperature.toString()
        val expectedSensorAndMoreHumidity = PercentageHundreds(sensorAndMoreHumidity).toPercentage().asText()
        cache.get("sensors:switchbot:${sensorAndMore.providerId}:humidity") shouldBeRight expectedSensorAndMoreHumidity
    }

}
