package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.sh.api.persistence.jdbc.dao.CacheDaoJdbcImpl
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@WebMvcTest(CacheController::class)
@Import(
    PersistedCacheService::class,
    CacheDaoJdbcImpl::class,
    SecurityConfig::class
)
@ActiveProfiles("test")
class CacheControllerUnitTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val cacheDao: CacheDao
) {
    private val testAuthSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// putCacheEntry ////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `putCacheEntry() authorization tests`(): List<DynamicTest> {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        return testAuthSupport.dynamicTestsBy{
            put("/cache/$key")
                .contentType("application/json")
                .content("""{"value": "$value", "ttl": $ttl}""")
        }
    }

    @Test fun `putCacheEntry() return 500 when fails to persist entry`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { cacheDao.upsert(key, value, ttl)  } throws Exception()
        val request = put("/cache/$key")
            .authenticated()
            .contentType("application/json")
            .content("""{"value": "$value", "ttl": $ttl}""")

        val response: MessageResponse = performRequestAndGet(request,
            status().isInternalServerError)

        response.message shouldBe "Failed to upsert cache entry"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getCacheEntry ////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `getCacheEntry() authorization tests`(): List<DynamicTest> {
        val key = aRandomUniqueString()
        return testAuthSupport.dynamicTestsBy{
            get("/cache/$key")
        }
    }

    @Test fun `getCacheEntry() return 500 when fails to get entry`() {
        val key = aRandomUniqueString()
        every { cacheDao.getEntry(key) } throws Exception()
        val request = get("/cache/$key")
            .authenticated()

        val response: MessageResponse = performRequestAndGet(request,
            status().isInternalServerError)

        response.message shouldBe "Failed to get cache entry"    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// postCacheBatch ///////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory
    fun `postCacheBatch() authorization tests`(): List<DynamicTest> {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        return testAuthSupport.dynamicTestsBy {
            post("/cache/batch")
                .contentType("application/json")
                .content("""[{"key": "$key", "value": "$value"}]""")
        }
    }

    @Test fun `postCacheBatch() returns 400 when entries list is empty`() {
        val request = post("/cache/batch")
            .contentType("application/json")
            .authenticated()
            .content("[]")

        val response: MessageResponse = performRequestAndGet(request, status().isBadRequest)

        response.message shouldBe "There are no entries to persist"
    }

    @Test fun `postCacheBatch() returns 400 when the list has too many entries`() {
        val tooManyEntries = (1..(CacheController.MAX_BATCH_SIZE + 1)).map {
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString())
        }
        val request = post("/cache/batch")
            .contentType("application/json")
            .authenticated()
            .content(objectMapper.writeValueAsString(tooManyEntries))

        val response: MessageResponse = performRequestAndGet(request,
            status().isPayloadTooLarge)

        response.message shouldBe "Batch size exceeds the maximum of ${CacheController.MAX_BATCH_SIZE} entries"
    }

    @Test fun `postCacheBatch() returns 400 when the list has duplicates`() {
        val dupKey = aRandomUniqueString()
        val batchEntries = listOf(
            CacheEntryDto(dupKey, aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(dupKey, aRandomUniqueString())
        )
        val request = post("/cache/batch")
            .contentType("application/json")
            .authenticated()
            .content(objectMapper.writeValueAsString(batchEntries))

        val response: MessageResponse = performRequestAndGet(request,
            status().isBadRequest)

        response.message shouldBe "Batch contains duplicate keys"
    }

    @Test fun `postCacheBatch() return 500 when fails to persist batch entries`() {
        val failure = Exception("batch persist failure")
        val batchEntries = listOf(
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString())
        )
        every { cacheDao.upsertBatch(batchEntries) } throws failure
        val request = post("/cache/batch")
            .contentType("application/json")
            .authenticated()
            .content(objectMapper.writeValueAsString(batchEntries))

        val response: MessageResponse = performRequestAndGet(request, status().isInternalServerError)

        response.message shouldBe "Failed to persist batch"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private inline fun <reified T> performRequestAndGet(
        requestBuilder: MockHttpServletRequestBuilder,
        expectedStatus: ResultMatcher
    ): T {
        val responseBody = mockMvc.perform(requestBuilder)
            .andExpect(expectedStatus)
            .andReturn().response.contentAsString
        return objectMapper.readValue<T>(responseBody)
    }

}
