package org.agrfesta.sh.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class CacheControllerIntegrationTest(
    private val cacheRepository: CacheJdbcRepository,
    private val objectMapper: ObjectMapper
): AbstractIntegrationTest() {
    private val now = nowNoMills()

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()

        @Container
        @ServiceConnection
        val redis = createRedisContainer()
    }

    @BeforeEach
    fun init() {
        every { timeService.now() } returns now
    }

    ///// putCacheEntry ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `putCacheEntry() insert new entry when key is missing`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        val value = aRandomUniqueString()
        cacheRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"value": "$value", "ttl": $ttl}""")
            .`when`()
            .put("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Entry for key '$key' upserted successfully"
        val cacheEntry = cacheRepository.findEntry(key).shouldNotBeNull()
        cacheEntry.value shouldBe value
        cacheEntry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    @Test fun `putCacheEntry() entry never expires when inserted with no ttl`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        cacheRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"value": "$value"}""")
            .`when`()
            .put("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Entry for key '$key' upserted successfully"
        val cacheEntry = cacheRepository.findEntry(key).shouldNotBeNull()
        cacheEntry.value shouldBe value
        cacheEntry.expiresAt.shouldBeNull()
    }

    @Test fun `putCacheEntry() update value and ttl when key is already in cache`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        val value = aRandomUniqueString()
        cacheRepository.upsert(key, aRandomUniqueString(), aRandomTtl())
        cacheRepository.findEntry(key).shouldNotBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"value": "$value", "ttl": $ttl}""")
            .`when`()
            .put("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Entry for key '$key' upserted successfully"
        val cacheEntry = cacheRepository.findEntry(key).shouldNotBeNull()
        cacheEntry.value shouldBe value
        cacheEntry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getCacheEntry ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getCacheEntry() return 404 when key is missing`() {
        val key = aRandomUniqueString()
        cacheRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/cache/$key")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Key '$key' is missing"
    }

    @Test fun `getCacheEntry() return 404 when key is expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { timeService.now() } returns now andThen now andThen now andThen now.plusSeconds(ttl+10)
        cacheRepository.upsert(key, aRandomUniqueString(), ttl)
        cacheRepository.findEntry(key)

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/cache/$key")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Key '$key' is missing"
        verify(exactly = 4) { timeService.now() }
    }

    @Test fun `getCacheEntry() returns cache entry when is not missing nor expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { timeService.now() } returns now andThen now.plusSeconds(ttl-1)
        cacheRepository.upsert(key, aRandomUniqueString(), aRandomTtl())
        cacheRepository.findEntry(key)

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(CacheEntry::class.java)

        val cacheEntry = cacheRepository.findEntry(key)
        cacheEntry shouldBe result
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// postCacheBatch ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `postCacheBatch() returns 200 when successfully upserts all entries`() {
        val key1 = aRandomUniqueString()
        val value1 = aRandomUniqueString()
        val ttl1 = aRandomTtl()
        val key2 = aRandomUniqueString()
        val value2 = aRandomUniqueString()
        val ttl2 = aRandomTtl()
        val key3 = aRandomUniqueString()
        val value3 = aRandomUniqueString()
        val batchEntries = listOf(
            CacheEntryDto(key1, value1, ttl1),
            CacheEntryDto(key2, value2, ttl2),
            CacheEntryDto(key3, value3)
        )
        cacheRepository.findEntry(key1).shouldBeNull()
        cacheRepository.upsert(key2, aRandomUniqueString(), aRandomTtl()) // key2 is already in cache
        cacheRepository.findEntry(key2).shouldNotBeNull()
        cacheRepository.findEntry(key3).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body(objectMapper.writeValueAsString(batchEntries))
            .`when`()
            .post("/cache/batch")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Successfully persisted ${batchEntries.size} entries"
        val entry1 = cacheRepository.findEntry(key1).shouldNotBeNull()
        entry1.value shouldBe value1
        entry1.expiresAt shouldBe now.plusSeconds(ttl1)
        val entry2 = cacheRepository.findEntry(key2).shouldNotBeNull()
        entry2.value shouldBe value2 // verify updated value
        entry2.expiresAt shouldBe now.plusSeconds(ttl2) // verify updated ttl
        val entry3 = cacheRepository.findEntry(key3).shouldNotBeNull()
        entry3.value shouldBe value3
        entry3.expiresAt.shouldBeNull()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
