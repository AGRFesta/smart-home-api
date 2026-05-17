package org.agrfesta.sh.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.MessageResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PropertyIntegrationTest(
    private val propertyRepository: PropertyJdbcRepository,
    private val objectMapper: ObjectMapper
) : AbstractIntegrationTest() {
    private val now = nowNoMills()

    @BeforeEach
    fun init() {
        every { timeProvider.now() } returns now
    }

    // /// putPropertyEntry /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `putPropertyEntry() insert new entry when key is missing`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        val value = aRandomUniqueString()
        propertyRepository.findEntry(key).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"value": "$value", "ttl": $ttl}""")
            .`when`()
            .put("/properties/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Entry for key '$key' upserted successfully"
        val propertyEntry = propertyRepository.findEntry(key).shouldNotBeNull()
        propertyEntry.value shouldBe value
        propertyEntry.expiresAt shouldBe now.plusSeconds(ttl)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// getPropertyEntry /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getPropertyEntry() return 404 when key is expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { timeProvider.now() } returns now andThen now andThen now.plusSeconds(ttl + 10)
        propertyRepository.upsert(key, aRandomUniqueString(), ttl)
        propertyRepository.findEntry(key)

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/properties/$key")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Key '$key' is missing"
        verify(exactly = 3) { timeProvider.now() }
    }

    @Test fun `getPropertyEntry() returns property entry when is not missing nor expired`() {
        val key = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { timeProvider.now() } returns now andThen now.plusSeconds(ttl - 1)
        propertyRepository.upsert(key, aRandomUniqueString(), ttl)
        propertyRepository.findEntry(key)

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/properties/$key")
            .then()
            .statusCode(200)
            .extract()
            .`as`(PropertyEntry::class.java)

        val propertyEntry = propertyRepository.findEntry(key)
        propertyEntry shouldBe result
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// postPropertyBatch ////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `postPropertyBatch() returns 200 when successfully upserts all entries`() {
        val key1 = aRandomUniqueString()
        val value1 = aRandomUniqueString()
        val ttl1 = aRandomTtl()
        val key2 = aRandomUniqueString()
        val value2 = aRandomUniqueString()
        val ttl2 = aRandomTtl()
        val key3 = aRandomUniqueString()
        val value3 = aRandomUniqueString()
        val batchEntries = listOf(
            PropertyUpsertEntry(key1, value1, ttl1),
            PropertyUpsertEntry(key2, value2, ttl2),
            PropertyUpsertEntry(key3, value3)
        )
        propertyRepository.findEntry(key1).shouldBeNull()
        propertyRepository.upsert(key2, aRandomUniqueString(), aRandomTtl()) // key2 already exists
        propertyRepository.findEntry(key2).shouldNotBeNull()
        propertyRepository.findEntry(key3).shouldBeNull()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body(objectMapper.writeValueAsString(batchEntries))
            .`when`()
            .post("/properties/batch")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Successfully persisted ${batchEntries.size} entries"
        val entry1 = propertyRepository.findEntry(key1).shouldNotBeNull()
        entry1.value shouldBe value1
        entry1.expiresAt shouldBe now.plusSeconds(ttl1)
        val entry2 = propertyRepository.findEntry(key2).shouldNotBeNull()
        entry2.value shouldBe value2 // verify updated value
        entry2.expiresAt shouldBe now.plusSeconds(ttl2) // verify updated ttl
        val entry3 = propertyRepository.findEntry(key3).shouldNotBeNull()
        entry3.value shouldBe value3
        entry3.expiresAt.shouldBeNull()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
