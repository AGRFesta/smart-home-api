package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CacheControllerIntegrationTest(
    @Autowired private val cacheRepository: CacheJdbcRepository,
    @Autowired @MockkBean private val timeService: TimeService
) {

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("timescale/timescaledb:latest-pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }

    }

    @LocalServerPort private val port: Int? = null
    private val now = nowNoMills()

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
        every { timeService.now() } returns now
    }

    @Test fun `putCacheEntry() insert new entry when key is missing`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        val value = aRandomUniqueString()
        cacheRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"value": "$value", "ttl": $ttl}""")
            .`when`()
            .put("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Inserted key '$key' with value '$value'"
        val cacheEntry = cacheRepository.findEntry(key)
        cacheEntry.shouldNotBeNull()
        cacheEntry.value shouldBe value
        cacheEntry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    @Test fun `putCacheEntry() entry never expires when inserted with no ttl`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        cacheRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"value": "$value"}""")
            .`when`()
            .put("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Inserted key '$key' with value '$value'"
        val cacheEntry = cacheRepository.findEntry(key)
        cacheEntry.shouldNotBeNull()
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
            .body("""{"value": "$value", "ttl": $ttl}""")
            .`when`()
            .put("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Inserted key '$key' with value '$value'"
        val cacheEntry = cacheRepository.findEntry(key)
        cacheEntry.shouldNotBeNull()
        cacheEntry.value shouldBe value
        cacheEntry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getCacheEntry() return 404 when key is missing`() {
        val key = aRandomUniqueString()
        cacheRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
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
        cacheRepository.findEntry(key).shouldNotBeNull()

        val result = given()
            .contentType(ContentType.JSON)
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
        cacheRepository.findEntry(key).shouldNotBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .`when`()
            .get("/cache/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(CacheEntry::class.java)

        val cacheEntry = cacheRepository.findEntry(key)
        cacheEntry shouldBe result
    }

}
