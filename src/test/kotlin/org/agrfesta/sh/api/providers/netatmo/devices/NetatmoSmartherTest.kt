package org.agrfesta.sh.api.providers.netatmo.devices

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Post
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.configuration.SMART_HOME_OBJECT_MAPPER
import org.agrfesta.sh.api.controllers.createMockEngine
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.OFF
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.ON
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.UNDEFINED
import org.agrfesta.sh.api.core.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.core.domain.failures.KtorRequestFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheRepository
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoClientAsserter
import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.NETATMO_ACCESS_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.providers.netatmo.aNetatmoHomeStatus
import org.agrfesta.sh.api.providers.netatmo.aNetatmoRoomStatus
import org.agrfesta.sh.api.providers.netatmo.devices.NetatmoSmarther.Companion.MAX_SET_POINT_TEMPERATURE
import org.agrfesta.sh.api.providers.netatmo.devices.NetatmoSmarther.Companion.MIN_SET_POINT_TEMPERATURE
import org.agrfesta.sh.api.providers.netatmo.devices.NetatmoSmarther.Companion.SET_POINT_MODE
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheAsserter
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aJsonNode
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.junit.jupiter.api.Test

class NetatmoSmartherTest {
    private val accessToken = aRandomUniqueString()
    private val uuid: UUID = UUID.randomUUID()
    private val deviceProviderId: String = aRandomUniqueString()
    private val mapper = SMART_HOME_OBJECT_MAPPER
    private val config = NetatmoConfiguration(
        baseUrl = anUrl(),
        clientSecret = aRandomUniqueString(),
        clientId = aRandomUniqueString(),
        homeId = aRandomUniqueString(),
        roomId = aRandomUniqueString()
    )
    private val now = generateNoNanosInstant()

    private val timeService: TimeService = mockk()
    private val cache: Cache = mockk(relaxed = true)
    private val cacheRepository: CacheRepository = mockk(relaxed = true)
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)

    private val cacheAsserter = CacheAsserter(cache, cacheRepository)
    private val clientAsserter = NetatmoClientAsserter(config = config, registry = registry)

    private val client = NetatmoClient(config, cache, cacheRepository, mapper, engine)
    private val sut = NetatmoSmarther(
        uuid = uuid,
        deviceProviderId = deviceProviderId,
        homeId = config.homeId,
        roomId = config.roomId,
        client = client,
        timeService = timeService
    )

    init {
        // Default behaviour
        cacheAsserter.givenCacheEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY, accessToken)
        every { timeService.now() } returns now
    }

    ///// fetchReadings() //////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `fetchReadings() Returns sensor data`() {
        runBlocking {
            val expectedData = aRandomThermoHygroData()
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(aNetatmoRoomStatus(
                    humidity = expectedData.relativeHumidity.value.movePointRight(2).stripTrailingZeros(),
                    measuredTemperature = expectedData.temperature
                ))
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            val data = sut.fetchReadings()

            data.shouldBeRight().shouldBeInstanceOf<ThermoHygroDataValue>()
                .thermoHygroData.apply {
                    temperature shouldBe expectedData.temperature
                    relativeHumidity shouldBe expectedData.relativeHumidity
                }
            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `fetchReadings() Returns failure when home status fetch fails`() {
        runBlocking {
            clientAsserter.givenHomeStatusFetchFailure()

            sut.fetchReadings().shouldBeLeft().shouldBeInstanceOf<KtorRequestFailure>()

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `fetchReadings() Returns NetatmoContractBreak when home status has more than one room`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(aNetatmoRoomStatus(), aNetatmoRoomStatus())
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.fetchReadings().shouldBeLeft()
                .shouldBeInstanceOf<NetatmoContractBreak>().apply {
                    message shouldBe "Not expecting to have more than one room"
                }

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `fetchReadings() Returns NetatmoContractBreak when home status has no rooms`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(rooms = listOf())
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.fetchReadings().shouldBeLeft()
                .shouldBeInstanceOf<NetatmoContractBreak>().apply {
                    message shouldBe "Not expecting to have no rooms"
                }

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `fetchReadings() Returns NetatmoContractBreak when home status response is empty`() {
        runBlocking {
            clientAsserter.givenHomeStatusFetchResponse("{}")

            sut.fetchReadings().shouldBeLeft()
                .shouldBeInstanceOf<NetatmoContractBreak>().apply {
                    message shouldBe "Unexpected home status response"
                    response shouldBe "{}"
                }

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `fetchReadings() Returns NetatmoContractBreak when home status response is not valid`() {
        runBlocking {
            val response = """
                {
                  "status": "ok",
                  "time_server": 1762282424,
                  "body": {
                    "home": {
                      "id": "6759635b4f8cc3a6d8063736",
                      "rooms": [{}]
                    }
                  }
                }
            """.trimIndent()
            clientAsserter.givenHomeStatusFetchResponse(response)

            sut.fetchReadings().shouldBeLeft()
                .shouldBeInstanceOf<NetatmoContractBreak>().apply {
                    message shouldBe "Unexpected home status response"
                    response shouldBe response
                }

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getActuatorStatus() //////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `getActuatorStatus() Returns ON when mode is 'manual' and target temperature is max`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(
                    aNetatmoRoomStatus(
                        setpointMode = SET_POINT_MODE,
                        setpointTemperature = MAX_SET_POINT_TEMPERATURE,
                        setpointStartTime = now.minusSeconds(3600),
                        setpointEndTime = now.plusSeconds(3600)
                    )
                )
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.getActuatorStatus().shouldBeRight() shouldBe ON

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `getActuatorStatus() Returns OFF when mode is 'manual' and target temperature is min`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(
                    aNetatmoRoomStatus(
                        setpointMode = SET_POINT_MODE,
                        setpointTemperature = MIN_SET_POINT_TEMPERATURE,
                        setpointStartTime = now.minusSeconds(3600),
                        setpointEndTime = now.plusSeconds(3600)
                    )
                )
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.getActuatorStatus().shouldBeRight() shouldBe OFF

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `getActuatorStatus() Returns UNDEFINED when temperature is not max nor min`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(
                    aNetatmoRoomStatus(
                        setpointMode = SET_POINT_MODE,
                        setpointTemperature = Temperature.of("18.0"),
                        setpointStartTime = now.minusSeconds(3600),
                        setpointEndTime = now.plusSeconds(3600)
                    )
                )
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.getActuatorStatus().shouldBeRight() shouldBe UNDEFINED

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `getActuatorStatus() Returns UNDEFINED when mode is not 'manual'`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(
                    aNetatmoRoomStatus(
                        setpointMode = aRandomUniqueString(),
                        setpointTemperature = MIN_SET_POINT_TEMPERATURE,
                        setpointStartTime = now.minusSeconds(3600),
                        setpointEndTime = now.plusSeconds(3600)
                    )
                )
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.getActuatorStatus().shouldBeRight() shouldBe UNDEFINED

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    @Test
    fun `getActuatorStatus() Returns UNDEFINED when is actually out of defined temporal range`() {
        runBlocking {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(
                    aNetatmoRoomStatus(
                        setpointMode = SET_POINT_MODE,
                        setpointTemperature = MAX_SET_POINT_TEMPERATURE,
                        setpointStartTime = now.plusSeconds(3600),
                        setpointEndTime = now.plusSeconds(7200)
                    )
                )
            )
            clientAsserter.givenHomeStatusFetchResponse(homeStatus)

            sut.getActuatorStatus().shouldBeRight() shouldBe UNDEFINED

            clientAsserter.verifyHomeStatusFetchRequest(accessToken, config.homeId)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// on() /////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `on() Sets setpoint with temperature of 30 degree for one hour`() {
        runBlocking {
            val expectedEndTime = now.plusSeconds(3600)
            clientAsserter.givenSetStatusResponse(aJsonNode())

            sut.on().shouldBeRight()

            registry.verifyRequest(Post, "/api/setstate") { request ->
                request.headers[Authorization] shouldBe "Bearer $accessToken"
                val requestBody = request.body.toByteArray().decodeToString()
                val expectedJson = """
                    {
                      "home": {
                        "id": "${config.homeId}",
                        "rooms": [
                          {
                            "id": "${config.roomId}",
                            "therm_setpoint_temperature": 30, 
                            "therm_setpoint_end_time": ${expectedEndTime.epochSecond},
                            "therm_setpoint_mode": "manual"
                          }
                        ]
                      }
                    }
                """
                requestBody shouldEqualSpecifiedJson expectedJson
            }
        }
    }

    @Test
    fun `on() Returns a failure when fails to set setpoint`() {
        runBlocking {
            clientAsserter.givenSetStatusFailure()

            sut.on().shouldBeLeft().shouldBeInstanceOf<KtorRequestFailure>()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// on() /////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `off() Sets setpoint with temperature of 7 degree for one hour`() {
        runBlocking {
            val expectedEndTime = now.plusSeconds(3600)
            clientAsserter.givenSetStatusResponse(aJsonNode())

            sut.off().shouldBeRight()

            registry.verifyRequest(Post, "/api/setstate") { request ->
                request.headers[Authorization] shouldBe "Bearer $accessToken"
                val requestBody = request.body.toByteArray().decodeToString()
                val expectedJson = """
                    {
                      "home": {
                        "id": "${config.homeId}",
                        "rooms": [
                          {
                            "id": "${config.roomId}",
                            "therm_setpoint_temperature": 7, 
                            "therm_setpoint_end_time": ${expectedEndTime.epochSecond},
                            "therm_setpoint_mode": "manual"
                          }
                        ]
                      }
                    }
                """.trimIndent()
                requestBody shouldEqualSpecifiedJson expectedJson
            }
        }
    }

    @Test
    fun `off() Returns a failure when fails to set setpoint`() {
        runBlocking {
            clientAsserter.givenSetStatusFailure()

            sut.off().shouldBeLeft().shouldBeInstanceOf<KtorRequestFailure>()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun generateNoNanosInstant(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
}
