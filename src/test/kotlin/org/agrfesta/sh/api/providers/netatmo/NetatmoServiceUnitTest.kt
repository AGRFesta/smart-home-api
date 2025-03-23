package org.agrfesta.sh.api.providers.netatmo

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.domain.failures.ExceptionFailure
import org.agrfesta.sh.api.persistence.jdbc.dao.CacheDaoJdbcImpl
import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheError
import org.agrfesta.sh.api.utils.CacheOkResponse
import org.agrfesta.sh.api.utils.CachedValueNotFound
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class NetatmoServiceUnitTest {
    private val accessToken = aRandomUniqueString()
    private val refreshToken = aRandomUniqueString()
    private val mapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val name = aRandomUniqueString()
    private val homeId = aRandomUniqueString()
    private val roomId = aRandomUniqueString()
    private val deviceId = aRandomUniqueString()
    private val userId = aRandomUniqueString()
    private val homeDataResp = mapper.aHomeData(name, homeId, roomId, deviceId, userId)

    private val cache: Cache = mockk()
    private val cacheRepository: CacheJdbcRepository = mockk()
    private val netatmoClient: NetatmoClient = mockk()

    private val sut = NetatmoService(
        cache = cache,
        cacheService = PersistedCacheService(CacheDaoJdbcImpl(cacheRepository)),
        netatmoClient = netatmoClient,
        objectMapper = mapper
    )

    init {
        every { cache.get("provider.netatmo.access-token") } returns accessToken.right()
    }

    ///// getAllDevices() //////////////////////////////////////////////////////////////////////////////////////////////
    @Test fun `getAllDevices() returns single device`() {
        coEvery { netatmoClient.getHomesData(accessToken) } returns homeDataResp.right()

        val res = sut.getAllDevices()

        val devices = res.shouldBeRight()
        devices shouldHaveSize 1
        val device = devices.first()
        device.providerId shouldBe deviceId
        device.name shouldBe name
        device.provider shouldBe NETATMO
        device.features.shouldContainExactlyInAnyOrder(SENSOR, ACTUATOR)
    }

    @Test fun `getAllDevices() refresh token when access token is missing`() {
        val expectedResponse = NetatmoRefreshTokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
        val prevRefreshToken = aRandomUniqueString()
        every {
            cache.get("provider.netatmo.access-token")
        } returns CachedValueNotFound("provider.netatmo.access-token").left()
        every { cacheRepository.findEntry("provider.netatmo.refresh-token") } returns CacheEntry(prevRefreshToken)
        coEvery { netatmoClient.refreshToken(prevRefreshToken) } returns expectedResponse.right()
        every {
            cacheRepository.upsert("provider.netatmo.refresh-token", expectedResponse.refreshToken)
        } returns Unit
        every { cache.set("provider.netatmo.access-token", expectedResponse.accessToken) } returns CacheOkResponse
        coEvery { netatmoClient.getHomesData(accessToken) } returns homeDataResp.right()

        val res = sut.getAllDevices()

        val devices = res.shouldBeRight()
        devices shouldHaveSize 1
    }

    @Test fun `getAllDevices() returns a failure when fails to fetch access token`() {
        val cacheFetchFailure = Exception("cache fetch failure")
        every {
            cache.get("provider.netatmo.access-token")
        } returns CacheError(cacheFetchFailure).left()

        val res = sut.getAllDevices()

        val failure = res.shouldBeLeft()
        failure.shouldBeInstanceOf<CacheError>()
        failure.exception shouldBe cacheFetchFailure
        verify(exactly = 0) { cacheRepository.findEntry(any()) }
        coVerify(exactly = 0) { netatmoClient.refreshToken(any()) }
        verify(exactly = 0) { cacheRepository.upsert(any(), any()) }
        verify(exactly = 0) { cache.set(any(), any()) }
        coVerify(exactly = 0) { netatmoClient.getHomesData(any()) }
    }

    @Test fun `getAllDevices() returns a failure when fails to fetch refresh token`() {
        val tokenFetchFailure = Exception("token fetch failure")
        every {
            cache.get("provider.netatmo.access-token")
        } returns CachedValueNotFound("provider.netatmo.access-token").left()
        every { cacheRepository.findEntry("provider.netatmo.refresh-token") } throws tokenFetchFailure

        val res = sut.getAllDevices()

        val failure = res.shouldBeLeft()
        failure.shouldBeInstanceOf<ExceptionFailure>()
        failure.exception shouldBe tokenFetchFailure
        coVerify(exactly = 0) { netatmoClient.refreshToken(any()) }
        verify(exactly = 0) { cacheRepository.upsert(any(), any()) }
        verify(exactly = 0) { cache.set(any(), any()) }
        coVerify(exactly = 0) { netatmoClient.getHomesData(any()) }
    }

    @Test fun `getAllDevices() returns a failure when fails to refresh token`() {
        val prevRefreshToken = aRandomUniqueString()
        val refreshTokenFailure = Exception("refresh token failure")
        every {
            cache.get("provider.netatmo.access-token")
        } returns CachedValueNotFound("provider.netatmo.access-token").left()
        every { cacheRepository.findEntry("provider.netatmo.refresh-token") } returns CacheEntry(prevRefreshToken)
        coEvery { netatmoClient.refreshToken(prevRefreshToken) } returns NetatmoAuthFailure(refreshTokenFailure).left()

        val res = sut.getAllDevices()

        val failure = res.shouldBeLeft()
        failure.shouldBeInstanceOf<ExceptionFailure>()
        failure.exception shouldBe refreshTokenFailure
        verify(exactly = 0) { cacheRepository.upsert(any(), any()) }
        verify(exactly = 0) { cache.set(any(), any()) }
        coVerify(exactly = 0) { netatmoClient.getHomesData(any()) }
    }

    @Test fun `getAllDevices() refresh token ignores token upsert failure`() {
        val expectedResponse = NetatmoRefreshTokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
        val prevRefreshToken = aRandomUniqueString()
        every {
            cache.get("provider.netatmo.access-token")
        } returns CachedValueNotFound("provider.netatmo.access-token").left()
        every { cacheRepository.findEntry("provider.netatmo.refresh-token") } returns CacheEntry(prevRefreshToken)
        coEvery { netatmoClient.refreshToken(prevRefreshToken) } returns expectedResponse.right()
        every {
            cacheRepository.upsert("provider.netatmo.refresh-token", expectedResponse.refreshToken)
        } throws Exception("failure")
        every { cache.set("provider.netatmo.access-token", expectedResponse.accessToken) } returns CacheOkResponse
        coEvery { netatmoClient.getHomesData(accessToken) } returns homeDataResp.right()

        val res = sut.getAllDevices()

        val devices = res.shouldBeRight()
        devices shouldHaveSize 1
    }

    @Test fun `getAllDevices() get device using access token without refreshing token`() {
        every { cache.get("provider.netatmo.access-token") } returns accessToken.right()
        coEvery { netatmoClient.getHomesData(accessToken) } returns homeDataResp.right()

        val res = sut.getAllDevices()

        val devices = res.shouldBeRight()
        devices shouldHaveSize 1
        verify(exactly = 0) { cacheRepository.findEntry(any()) }
        coVerify(exactly = 0) { netatmoClient.refreshToken(any()) }
        verify(exactly = 0) { cacheRepository.upsert(any(), any()) }
        verify(exactly = 0) { cache.set(any(), any()) }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
