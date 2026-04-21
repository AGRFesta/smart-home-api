package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.PropertyRepository
import org.agrfesta.sh.api.persistence.PropertyEntryDto
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

@WebMvcTest(PropertyController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("test")
class PropertyControllerMvcSliceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val propertyRepository: PropertyRepository
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// putPropertyEntry /////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `putPropertyEntry() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/properties/${aRandomUniqueString()}")
            .contentType("application/json")
            .content("""{"value": "${aRandomUniqueString()}"}""")
    }

    @Test fun `putPropertyEntry() returns 500 when fails to persist entry`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { propertyRepository.upsert(key, value, ttl) } returns PersistenceFailure(Exception()).left()

        val responseBody: String = mockMvc.perform(
            put("/properties/$key")
                .contentType("application/json")
                .authenticated()
                .content("""{"value": "$value", "ttl": $ttl}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to upsert property entry"
    }

    @Test fun `putPropertyEntry() returns 200 on success`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { propertyRepository.upsert(key, value, ttl) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            put("/properties/$key")
                .contentType("application/json")
                .authenticated()
                .content("""{"value": "$value", "ttl": $ttl}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Entry for key '$key' upserted successfully"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// postPropertyBatch ////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `postPropertyBatch() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/properties/batch")
            .contentType("application/json")
            .content("""[{"key": "${aRandomUniqueString()}", "value": "${aRandomUniqueString()}"}]""")
    }

    @Test fun `postPropertyBatch() returns 400 when entries list is empty`() {
        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content("[]"))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "There are no entries to persist"
    }

    @Test fun `postPropertyBatch() returns 413 when the list has too many entries`() {
        val tooManyEntries = (1..(PropertyController.MAX_BATCH_SIZE + 1)).map {
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString())
        }
        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(tooManyEntries)))
            .andExpect(status().isPayloadTooLarge)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Batch size exceeds the maximum of ${PropertyController.MAX_BATCH_SIZE} entries"
    }

    @Test fun `postPropertyBatch() returns 400 when the list has duplicates`() {
        val dupKey = aRandomUniqueString()
        val batchEntries = listOf(
            PropertyEntryDto(dupKey, aRandomUniqueString(), aRandomTtl()),
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyEntryDto(dupKey, aRandomUniqueString())
        )
        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries)))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Batch contains duplicate keys"
    }

    @Test fun `postPropertyBatch() returns 500 when fails to persist batch entries`() {
        val batchEntries = listOf(
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString())
        )
        every { propertyRepository.upsertBatch(batchEntries) } returns
                PersistenceFailure(Exception("batch persist failure")).left()
        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries)))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to persist batch"
    }

    @Test fun `postPropertyBatch() returns 200 on success`() {
        val batchEntries = listOf(
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyEntryDto(aRandomUniqueString(), aRandomUniqueString())
        )
        every { propertyRepository.upsertBatch(batchEntries) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Successfully persisted ${batchEntries.size} entries"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getPropertyEntry /////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getPropertyEntry() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/properties/${aRandomUniqueString()}")
    }

    @Test fun `getPropertyEntry() returns 500 when fails to get entry`() {
        val key = aRandomUniqueString()
        every { propertyRepository.getEntry(key) } returns PersistenceFailure(Exception()).left()

        val responseBody: String = mockMvc.perform(
            get("/properties/$key")
                .authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to get property entry"
    }

    @Test fun `getPropertyEntry() returns 404 when key is not found`() {
        val key = aRandomUniqueString()
        every { propertyRepository.getEntry(key) } returns PropertyNotFound.left()

        val responseBody: String = mockMvc.perform(
            get("/properties/$key")
                .authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Key '$key' is missing"
    }

    @Test fun `getPropertyEntry() returns 200 with entry on success`() {
        val key = aRandomUniqueString()
        val entry = PropertyEntry(value = aRandomUniqueString())
        every { propertyRepository.getEntry(key) } returns entry.right()

        val responseBody: String = mockMvc.perform(
            get("/properties/$key")
                .authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: PropertyEntry = objectMapper.readValue(responseBody, PropertyEntry::class.java)
        response shouldBe entry
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
