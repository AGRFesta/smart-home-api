package org.agrfesta.sh.api.providers.netatmo.devices

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Post
import io.mockk.every
import io.mockk.mockk
import java.util.*
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.configuration.SMART_HOME_OBJECT_MAPPER
import org.agrfesta.sh.api.controllers.createMockEngine
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.domain.failures.KtorRequestFailure
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoClientAsserter
import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak
import org.agrfesta.sh.api.providers.netatmo.NetatmoHomeStatusChange
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.ACCESS_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.providers.netatmo.aNetatmoHomeStatus
import org.agrfesta.sh.api.providers.netatmo.aNetatmoRoomStatus
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheAsserter
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.sh.api.utils.generateNoNanosInstant
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
    private val cacheDao: CacheDao = mockk(relaxed = true)
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)

    private val cacheAsserter = CacheAsserter(cache, cacheDao)
    private val clientAsserter = NetatmoClientAsserter(config = config, registry = registry)

    private val cacheService = PersistedCacheService(cacheDao)
    private val client = NetatmoClient(config, cache, cacheService, mapper, engine)
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
        cacheAsserter.givenCacheEntry(ACCESS_TOKEN_CACHE_KEY, accessToken)
        every { timeService.now() } returns now
    }

    ///// fetchReadings() //////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `fetchReadings() Returns sensor data`() {
        runBlocking {
            val expectedData = aRandomThermoHygroData()
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(aNetatmoRoomStatus(
                    humidity = expectedData.relativeHumidity.toHundreds(),
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

    ///// on() /////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun `on() Sets setpoint with temperature of 7 degree for one hour`() {
        runBlocking {
            val expectedEndTime = now.plusSeconds(3600)
            clientAsserter.givenSetStatusResponse(aJsonNode())

            sut.on().shouldBeRight()

            registry.verifyRequest(Post, "/api/setstate") { request ->
                request.headers[Authorization] shouldBe "Bearer $accessToken"
                val requestBody = request.body.toByteArray().decodeToString()
                val rootNode = mapper.readTree(requestBody)
                val homeNode = rootNode.at("/home")
                val endTime = homeNode.at("/rooms/0/therm_setpoint_end_time").longValue()
                endTime shouldBe expectedEndTime.epochSecond
                val requestedStatus = mapper.treeToValue(homeNode, NetatmoHomeStatusChange::class.java)
                requestedStatus.id shouldBe config.homeId
                requestedStatus.rooms.shouldHaveSize(1).first().apply {
                    id shouldBe config.roomId
                    setPointTemperature shouldBe Temperature("7.0")
                    setPointEndTime shouldBe expectedEndTime
                    setPointMode shouldBe "manual"
                }
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
    fun `off() Sets setpoint with temperature of 40 degree for one hour`() {
        runBlocking {
            val expectedEndTime = now.plusSeconds(3600)
            clientAsserter.givenSetStatusResponse(aJsonNode())

            sut.off().shouldBeRight()

            registry.verifyRequest(Post, "/api/setstate") { request ->
                request.headers[Authorization] shouldBe "Bearer $accessToken"
                val requestBody = request.body.toByteArray().decodeToString()
                val rootNode = mapper.readTree(requestBody)
                val homeNode = rootNode.at("/home")
                val endTime = homeNode.at("/rooms/0/therm_setpoint_end_time").longValue()
                endTime shouldBe expectedEndTime.epochSecond
                val requestedStatus = mapper.treeToValue(homeNode, NetatmoHomeStatusChange::class.java)
                requestedStatus.id shouldBe config.homeId
                requestedStatus.rooms.shouldHaveSize(1).first().apply {
                    id shouldBe config.roomId
                    setPointTemperature shouldBe Temperature("40.0")
                    setPointEndTime shouldBe expectedEndTime
                    setPointMode shouldBe "manual"
                }
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


}
