package org.agrfesta.sh.api.schedulers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.redis.testcontainers.RedisContainer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.mockk.coEvery
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDeviceStatusResponse
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.test.mothers.aRandomHumidity
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
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val cache: Cache,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient
) {

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @ServiceConnection
        val redis = RedisContainer(DockerImageName.parse("redis:7.0.10"))

    }

    @Test
    fun `fetchDevicesData() caches SwitchBot sensors device values only and ignores failures`() {
        val sensorTemperature = aRandomTemperature()
        val sensorHumidity = aRandomHumidity()
        val sensorAndMoreTemperature = aRandomTemperature()
        val sensorAndMoreHumidity = aRandomHumidity()
        val sensor = aDevice(features = setOf(SENSOR))
        devicesRepository.persist(sensor)
        val sensorAndMore = aDevice(features = setOf(SENSOR, ACTUATOR))
        devicesRepository.persist(sensorAndMore)
        val faultySensor = aDevice(features = setOf(SENSOR))
        devicesRepository.persist(faultySensor)
        val device = aDevice(features = emptySet())
        devicesRepository.persist(device)
        coEvery { switchBotDevicesClient.getDeviceStatus(sensor.providerId) } returns
                mapper.aSwitchBotDeviceStatusResponse(
                    humidity = sensorHumidity,
                    temperature = sensorTemperature
                )
        coEvery { switchBotDevicesClient.getDeviceStatus(sensorAndMore.providerId) } returns
                mapper.aSwitchBotDeviceStatusResponse(
                    humidity = sensorAndMoreHumidity,
                    temperature = sensorAndMoreTemperature
                )
        coEvery { switchBotDevicesClient.getDeviceStatus(faultySensor.providerId) } throws
                Exception("sensor readings failure")

        sut.fetchDevicesData()

        cache.get("sensors:switchbot:${sensor.providerId}:temperature") shouldBeRight sensorTemperature.toString()
        cache.get("sensors:switchbot:${sensor.providerId}:humidity") shouldBeRight sensorHumidity.toString()
        cache.get("sensors:switchbot:${sensorAndMore.providerId}:temperature") shouldBeRight sensorAndMoreTemperature.toString()
        cache.get("sensors:switchbot:${sensorAndMore.providerId}:humidity") shouldBeRight sensorAndMoreHumidity.toString()
    }

}
