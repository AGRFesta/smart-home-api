package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.domain.failures.PersistedCacheEntryNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.CacheDao
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CacheController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("test")
class CacheControllerMvcSliceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val cacheDao: CacheDao
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// putCacheEntry ////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `putCacheEntry() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/cache/${aRandomUniqueString()}")
            .contentType("application/json")
            .content("""{"value": "${aRandomUniqueString()}"}""")
    }

    @Test fun `putCacheEntry() returns 500 when fails to persist entry`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { cacheDao.upsert(key, value, ttl) } returns PersistenceFailure(Exception()).left()

        val responseBody: String = mockMvc.perform(
            put("/cache/$key")
                .contentType("application/json")
                .authenticated()
                .content("""{"value": "$value", "ttl": $ttl}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to upsert cache entry"
    }

    @Test fun `putCacheEntry() returns 200 on success`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { cacheDao.upsert(key, value, ttl) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            put("/cache/$key")
                .contentType("application/json")
                .authenticated()
                .content("""{"value": "$value", "ttl": $ttl}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Entry for key '$key' upserted successfully"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// postCacheBatch ///////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `postCacheBatch() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/cache/batch")
            .contentType("application/json")
            .content("""[{"key": "${aRandomUniqueString()}", "value": "${aRandomUniqueString()}"}]""")
    }

    @Test fun `postCacheBatch() returns 400 when entries list is empty`() {
        val responseBody: String = mockMvc.perform(
            post("/cache/batch")
                .contentType("application/json")
                .authenticated()
                .content("[]"))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "There are no entries to persist"
    }

    @Test fun `postCacheBatch() returns 413 when the list has too many entries`() {
        val tooManyEntries = (1..(CacheController.MAX_BATCH_SIZE + 1)).map {
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString())
        }
        val responseBody: String = mockMvc.perform(
            post("/cache/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(tooManyEntries)))
            .andExpect(status().isPayloadTooLarge)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Batch size exceeds the maximum of ${CacheController.MAX_BATCH_SIZE} entries"
    }

    @Test fun `postCacheBatch() returns 400 when the list has duplicates`() {
        val dupKey = aRandomUniqueString()
        val batchEntries = listOf(
            CacheEntryDto(dupKey, aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(dupKey, aRandomUniqueString())
        )
        val responseBody: String = mockMvc.perform(
            post("/cache/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries)))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Batch contains duplicate keys"
    }

    @Test fun `postCacheBatch() returns 500 when fails to persist batch entries`() {
        val batchEntries = listOf(
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString())
        )
        every { cacheDao.upsertBatch(batchEntries) } returns
                PersistenceFailure(Exception("batch persist failure")).left()
        val responseBody: String = mockMvc.perform(
            post("/cache/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries)))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to persist batch"
    }

    @Test fun `postCacheBatch() returns 200 on success`() {
        val batchEntries = listOf(
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            CacheEntryDto(aRandomUniqueString(), aRandomUniqueString())
        )
        every { cacheDao.upsertBatch(batchEntries) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            post("/cache/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Successfully persisted ${batchEntries.size} entries"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getCacheEntry ////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getCacheEntry() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/cache/${aRandomUniqueString()}")
    }

    @Test fun `getCacheEntry() returns 500 when fails to get entry`() {
        val key = aRandomUniqueString()
        every { cacheDao.getEntry(key) } returns PersistenceFailure(Exception()).left()

        val responseBody: String = mockMvc.perform(
            get("/cache/$key")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to get cache entry"
    }

    @Test fun `getCacheEntry() returns 404 when key is not found`() {
        val key = aRandomUniqueString()
        every { cacheDao.getEntry(key) } returns PersistedCacheEntryNotFound.left()

        val responseBody: String = mockMvc.perform(
            get("/cache/$key")
                .authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Key '$key' is missing"
    }

    @Test fun `getCacheEntry() returns 200 with entry on success`() {
        val key = aRandomUniqueString()
        val entry = CacheEntry(value = aRandomUniqueString())
        every { cacheDao.getEntry(key) } returns entry.right()

        val responseBody: String = mockMvc.perform(
            get("/cache/$key")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: CacheEntry = objectMapper.readValue(responseBody, CacheEntry::class.java)
        response shouldBe entry
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
