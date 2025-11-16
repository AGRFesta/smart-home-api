package org.agrfesta.sh.api.providers.netatmo

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.configuration.SMART_HOME_OBJECT_MAPPER
import org.agrfesta.sh.api.controllers.createMockEngine
import org.agrfesta.sh.api.domain.failures.KtorRequestFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.NETATMO_ACCESS_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.NETATMO_REFRESH_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheAsserter
import org.agrfesta.test.mothers.aJsonNode
import org.agrfesta.test.mothers.aRandomNonNegativeInt
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.anUrl
import org.junit.jupiter.api.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class NetatmoClientTest {
    private val accessToken = aRandomUniqueString()
    private val expiresSeconds = aRandomNonNegativeInt()
    private val mapper = SMART_HOME_OBJECT_MAPPER
    private val config = NetatmoConfiguration(
        baseUrl = anUrl(),
        clientSecret = aRandomUniqueString(),
        clientId = aRandomUniqueString(),
        homeId = aRandomUniqueString(),
        roomId = aRandomUniqueString()
    )

    private val cache: Cache = mockk(relaxed = true)
    private val cacheDao: CacheDao = mockk(relaxed = true)
    private val registry = BehaviorRegistry()
    private val engine = createMockEngine(registry)

    private val cacheAsserter = CacheAsserter(cache, cacheDao)
    private val clientAsserter = NetatmoClientAsserter(config, registry, mapper)

    private val cacheService = PersistedCacheService(cacheDao)
    private val sut = NetatmoClient(config, cache, cacheService, mapper, engine)

    init {
        // Default behaviour
        cacheAsserter.givenCacheEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY, accessToken)
    }

    ///// refreshToken() ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `refreshToken() handle ok response`() {
        val prevRefreshToken = aRandomUniqueString()
        val expectedResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        runBlocking {
            clientAsserter.givenTokenFetchResponse(expectedResponse)

            val result = sut.refreshToken(prevRefreshToken)

            clientAsserter.verifyTokenFetchRequest(prevRefreshToken)
            result shouldBeRight expectedResponse
        }
    }

    @Test fun `refreshToken() handle error response`() {
        val prevRefreshToken = aRandomUniqueString()
        val errorMessage = aRandomUniqueString()
        runBlocking {
            clientAsserter.givenTokenFetchFailure(errorMessage)

            val result = sut.refreshToken(prevRefreshToken)

            val failure = result.shouldBeLeft()
            failure.exception.shouldBeInstanceOf<ClientRequestException>()
            clientAsserter.verifyTokenFetchRequest(prevRefreshToken)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getHomesData() ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHomesData() Returns default home data`() {
        val expectedResponse = aJsonNode()
        runBlocking {
            clientAsserter.givenHomeDataFetchResponse(expectedResponse)

            val result = sut.getHomesData()

            result shouldBeRight expectedResponse
            clientAsserter.verifyHomeDataFetchRequest(accessToken)
        }
    }

    @Test fun `getHomesData() Returns home data by home id`() {
        val homeId = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        runBlocking {
            clientAsserter.givenHomeDataFetchResponse(expectedResponse)

            val result = sut.getHomesData(homeId)

            result shouldBeRight expectedResponse
            clientAsserter.verifyHomeDataFetchRequest(accessToken, homeId)
        }
    }

    @Test fun `getHomesData() Returns home data and refreshes token when missing in cache`() {
        val homeId = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val refreshToken = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, refreshToken)
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenHomeDataFetchResponse(expectedResponse)

            val result = sut.getHomesData(homeId)

            result shouldBeRight expectedResponse
            clientAsserter.verifyTokenFetchRequest(refreshToken)
            clientAsserter.verifyHomeDataFetchRequest(refreshTokenResponse.accessToken, homeId)
            cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `getHomesData() Returns a failure and remove cached value when access token is not valid`() {
        runBlocking {
            clientAsserter.givenHomeDataFetchFailure(
                accessToken = accessToken,
                errorMessage = "Invalid access token",
                status = Forbidden)

            val result = sut.getHomesData()

            result.shouldBeLeft().shouldBeInstanceOf<NetatmoInvalidAccessToken>()
            cacheAsserter.verifyCacheEntryRemoval(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        }
    }

    @Test fun `getHomesData() Returns home data with refreshed token even when cache fails`() {
        val homeId = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val persistedRefreshToken = aRandomUniqueString()
        cacheAsserter.givenFailingCache()
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, persistedRefreshToken)
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenHomeDataFetchResponse(expectedResponse)

            val result = sut.getHomesData(homeId)

            result shouldBeRight expectedResponse
            clientAsserter.verifyTokenFetchRequest(persistedRefreshToken)
            clientAsserter.verifyHomeDataFetchRequest(refreshTokenResponse.accessToken, homeId)
            cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `getHomesData() Returns home data with refreshed token even when persisted cache set fails`() {
        val homeId = aRandomUniqueString()
        val expectedResponse = aJsonNode()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val persistedRefreshToken = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, persistedRefreshToken)
        cacheAsserter.givenPersistedCacheEntryUpsertFailure()
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenHomeDataFetchResponse(expectedResponse)

            val result = sut.getHomesData(homeId)

            result shouldBeRight expectedResponse
            clientAsserter.verifyTokenFetchRequest(persistedRefreshToken)
            clientAsserter.verifyHomeDataFetchRequest(refreshTokenResponse.accessToken, homeId)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `getHomesData() Returns a failure when fails to fetch home data`() {
        val homeId = aRandomUniqueString()
        runBlocking {
            clientAsserter.givenHomeDataFetchFailure()

            val result = sut.getHomesData(homeId)

            result.shouldBeLeft().shouldBeInstanceOf<KtorRequestFailure>()
            clientAsserter.verifyHomeDataFetchRequest(accessToken, homeId)
        }
    }

    @Test fun `getHomesData() Returns a failure when fails to fetch persisted token`() {
        val homeId = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntryFetchFailure()
        runBlocking {
            val result = sut.getHomesData(homeId)

            result.shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
            clientAsserter.verifyNoHomeStatusFetchRequest()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getHomeStatus() //////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHomeStatus() Returns home status`() {
        val homeId = aRandomUniqueString()
        val expectedHomeStatus = aNetatmoHomeStatus()
        runBlocking {
            clientAsserter.givenHomeStatusFetchResponse(expectedHomeStatus)

            val result = sut.getHomeStatus(homeId)

            result shouldBeRight expectedHomeStatus
            clientAsserter.verifyHomeStatusFetchRequest(accessToken, homeId)
        }
    }

    @Test fun `getHomeStatus() Returns a failure and remove cached value when access token is not valid`() {
        val homeId = aRandomUniqueString()
        runBlocking {
            clientAsserter.givenHomeStatusFetchFailure(
                errorMessage = "Invalid access token",
                status = Forbidden)

            val result = sut.getHomeStatus(homeId)

            result.shouldBeLeft().shouldBeInstanceOf<NetatmoInvalidAccessToken>()
            cacheAsserter.verifyCacheEntryRemoval(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        }
    }

    @Test fun `getHomeStatus() Returns home status and refreshes token when missing in cache`() {
        val homeId = aRandomUniqueString()
        val expectedHomeStatus = aNetatmoHomeStatus()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val refreshToken = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, refreshToken)
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenHomeStatusFetchResponse(expectedHomeStatus)

            val result = sut.getHomeStatus(homeId)

            result shouldBeRight expectedHomeStatus
            clientAsserter.verifyTokenFetchRequest(refreshToken)
            clientAsserter.verifyHomeStatusFetchRequest(refreshTokenResponse.accessToken, homeId)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `getHomeStatus() Returns home status with refreshed token even when cache fails`() {
        val homeId = aRandomUniqueString()
        val expectedHomeStatus = aNetatmoHomeStatus()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val persistedRefreshToken = aRandomUniqueString()
        cacheAsserter.givenFailingCache()
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, persistedRefreshToken)
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenHomeStatusFetchResponse(expectedHomeStatus)

            val result = sut.getHomeStatus(homeId)

            result shouldBeRight expectedHomeStatus
            clientAsserter.verifyTokenFetchRequest(persistedRefreshToken)
            clientAsserter.verifyHomeStatusFetchRequest(refreshTokenResponse.accessToken, homeId)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `getHomeStatus() Returns home status with refreshed token even when persisted cache set fails`() {
        val homeId = aRandomUniqueString()
        val expectedHomeStatus = aNetatmoHomeStatus()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val persistedRefreshToken = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, persistedRefreshToken)
        cacheAsserter.givenPersistedCacheEntryUpsertFailure()
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenHomeStatusFetchResponse(expectedHomeStatus)

            val result = sut.getHomeStatus(homeId)

            result shouldBeRight expectedHomeStatus
            clientAsserter.verifyTokenFetchRequest(persistedRefreshToken)
            clientAsserter.verifyHomeStatusFetchRequest(refreshTokenResponse.accessToken, homeId)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `getHomeStatus() Returns a failure when fails to fetch home status`() {
        val homeId = aRandomUniqueString()
        runBlocking {
            clientAsserter.givenHomeStatusFetchFailure()

            val result = sut.getHomeStatus(homeId)

            result.shouldBeLeft().shouldBeInstanceOf<KtorRequestFailure>()
            clientAsserter.verifyHomeStatusFetchRequest(accessToken, homeId)
        }
    }

    @Test fun `getHomeStatus() Returns a failure when fails to fetch persisted token`() {
        val homeId = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntryFetchFailure()
        runBlocking {
            val result = sut.getHomeStatus(homeId)

            result.shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
            clientAsserter.verifyNoHomeStatusFetchRequest()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// setState() ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `setState() Returns NetatmoSetStatusSuccess when successfully sets home status`() {
        val newStatus = aNetatmoHomeStatusChange()
        val expectedResponse = aJsonNode()
        runBlocking {
            clientAsserter.givenSetStatusResponse(expectedResponse)

            val result = sut.setState(newStatus)

            result shouldBeRight NetatmoSetStatusSuccess
            clientAsserter.verifySetStatusRequest(accessToken, newStatus)
        }
    }

    @Test fun `setState() Returns a failure and remove cached value when access token is not valid`() {
        val newStatus = aNetatmoHomeStatusChange()
        runBlocking {
            clientAsserter.givenSetStatusFailure(
                errorMessage = "Invalid access token",
                status = Forbidden)

            val result = sut.setState(newStatus)

            result.shouldBeLeft().shouldBeInstanceOf<NetatmoInvalidAccessToken>()
            cacheAsserter.verifyCacheEntryRemoval(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        }
    }

    @Test fun `setState() Refreshes token when missing in cache`() {
        val newStatus = aNetatmoHomeStatusChange()
        val expectedResponse = aJsonNode()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val refreshToken = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, refreshToken)
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenSetStatusResponse(expectedResponse)

            val result = sut.setState(newStatus)

            result shouldBeRight NetatmoSetStatusSuccess
            clientAsserter.verifyTokenFetchRequest(refreshToken)
            clientAsserter.verifySetStatusRequest(refreshTokenResponse.accessToken, newStatus)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `setState() Refreshes token even when cache fails`() {
        val newStatus = aNetatmoHomeStatusChange()
        val expectedResponse = aJsonNode()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val persistedRefreshToken = aRandomUniqueString()
        cacheAsserter.givenFailingCache()
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, persistedRefreshToken)
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenSetStatusResponse(expectedResponse)

            val result = sut.setState(newStatus)

            result shouldBeRight NetatmoSetStatusSuccess
            clientAsserter.verifyTokenFetchRequest(persistedRefreshToken)
            clientAsserter.verifySetStatusRequest(refreshTokenResponse.accessToken, newStatus)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `setState() Refreshes token even when persisted cache set fails`() {
        val newStatus = aNetatmoHomeStatusChange()
        val expectedResponse = aJsonNode()
        val refreshTokenResponse = NetatmoRefreshTokenResponse(
            accessToken = aRandomUniqueString(),
            refreshToken = aRandomUniqueString(),
            expiresIn = expiresSeconds
        )
        val persistedRefreshToken = aRandomUniqueString()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntry(NETATMO_REFRESH_TOKEN_CACHE_KEY, persistedRefreshToken)
        cacheAsserter.givenPersistedCacheEntryUpsertFailure()
        runBlocking {
            clientAsserter.givenTokenFetchResponse(refreshTokenResponse)
            clientAsserter.givenSetStatusResponse(expectedResponse)

            val result = sut.setState(newStatus)

            result shouldBeRight NetatmoSetStatusSuccess
            clientAsserter.verifyTokenFetchRequest(persistedRefreshToken)
            clientAsserter.verifySetStatusRequest(refreshTokenResponse.accessToken, newStatus)
                        cacheAsserter.verifyCacheEntrySet(
                NETATMO_ACCESS_TOKEN_CACHE_KEY,
                refreshTokenResponse.accessToken,
                expiresSeconds.toDuration(DurationUnit.SECONDS))
            cacheAsserter.verifyPersistedCacheEntryUpsert(
                NETATMO_REFRESH_TOKEN_CACHE_KEY,
                refreshTokenResponse.refreshToken)
        }
    }

    @Test fun `setState() Returns a failure when fails to set home status`() {
        val newStatus = aNetatmoHomeStatusChange()
        runBlocking {
            clientAsserter.givenSetStatusFailure()

            val result = sut.setState(newStatus)

            result.shouldBeLeft().shouldBeInstanceOf<KtorRequestFailure>()
            clientAsserter.verifySetStatusRequest(accessToken, newStatus)
        }
    }

    @Test fun `setState() Returns a failure when fails to fetch persisted token`() {
        val newStatus = aNetatmoHomeStatusChange()
        cacheAsserter.givenMissingEntry(NETATMO_ACCESS_TOKEN_CACHE_KEY)
        cacheAsserter.givenPersistedCacheEntryFetchFailure()
        runBlocking {
            val result = sut.setState(newStatus)

            result.shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
            clientAsserter.verifyNoSetStatusRequest()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
