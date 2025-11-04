package org.agrfesta.sh.api.schedulers

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.redis.testcontainers.RedisContainer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.commons.PercentageHundreds
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDeviceStatusResponse
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.getThermoHygroKey
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
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
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient //TODO move it in a dedicated test configuration, should be configured trough asserter only
) {

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

    @Test fun `fetchDevicesData() caches SwitchBot sensors device values only and ignores failures`() {
        val sensorData = aRandomThermoHygroData(
            relativeHumidity = PercentageHundreds(aRandomIntHumidity()).toPercentage())
        val sensorSWB = aDeviceDataValue(provider = Provider.SWITCHBOT, features = setOf(SENSOR))
            .apply { devicesRepository.persist(this) }
        val faultySensorSWB = aDeviceDataValue(features = setOf(SENSOR))
            .apply { devicesRepository.persist(this) }
        aDeviceDataValue(features = emptySet()).apply { devicesRepository.persist(this) }
        switchBotClientAsserter.givenSensorData(sensorSWB.deviceProviderId, sensorData)
        switchBotClientAsserter.givenSensorDataFailure(faultySensorSWB.deviceProviderId)

        sut.fetchDevicesData()

        //TODO move these assertions in a SmartCacheAsserter
        cache.get(sensorSWB.getThermoHygroKey()) shouldBeRightJson """{
            "h":"${sensorData.relativeHumidity.value}",
            "t":"${sensorData.temperature}"}
        """.trimIndent()
//        cache.get(sensorAndMore.getThermoHygroKey()) shouldBeRightJson """{
//            "h":"${sensorAndMoreData.relativeHumidity.value}",
//            "t":"${sensorAndMoreData.temperature}"}
//        """.trimIndent()
    }

    @TestFactory
    fun correctlyCacheSwitchBotTemperatureTextValues() = listOf(
        "0", "33.3333", "-41", "100", "-15.22"
    ).map {
        dynamicTest(it) {
            val response = objectMapper.aSwitchBotDeviceStatusResponse(temperatureText = it)
            val sensor = aDeviceDataValue(provider = Provider.SWITCHBOT, features = setOf(SENSOR))
            devicesRepository.persist(sensor)
            coEvery { switchBotDevicesClient.getDeviceStatus(sensor.deviceProviderId) } returns response

            sut.fetchDevicesData()

            val json = cache.get(sensor.getThermoHygroKey()).shouldBeRight()
            val res: JsonNode = objectMapper.readTree(json)
            res.at("/t").asText() shouldBe it
        }
    }

    private infix fun <A> Either<A, String>.shouldBeRightJson(b: String): JsonNode {
        val json = shouldBeRight()
        val jsonNode = objectMapper.readTree(json)
        jsonNode shouldBe objectMapper.readTree(b)
        return jsonNode
    }

}
