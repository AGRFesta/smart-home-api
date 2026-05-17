package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.inbounds.GetPropertyUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyBatchUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.UpsertPropertyUseCase
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.DuplicatePropertyKeys
import org.agrfesta.sh.api.core.domain.failures.EmptyPropertyBatch
import org.agrfesta.sh.api.core.domain.failures.PropertyBatchTooLarge
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aRandomTtl
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PropertyController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class PropertyControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val upsertPropertyUseCase: UpsertPropertyUseCase,
    @MockkBean private val upsertPropertyBatchUseCase: UpsertPropertyBatchUseCase,
    @MockkBean private val getPropertyUseCase: GetPropertyUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    // /// putPropertyEntry /////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `putPropertyEntry() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/properties/${aRandomUniqueString()}")
            .contentType("application/json")
            .content("""{"value": "${aRandomUniqueString()}"}""")
    }

    @Test fun `putPropertyEntry() returns 500 when fails to persist entry`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { upsertPropertyUseCase.execute(key, value, ttl) } returns PropertyRepositoryError.left()

        val responseBody: String = mockMvc.perform(
            put("/properties/$key")
                .contentType("application/json")
                .authenticated()
                .content("""{"value": "$value", "ttl": $ttl}""")
        )
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to upsert property entry"
    }

    @Test fun `putPropertyEntry() returns 200 on success`() {
        val key = aRandomUniqueString()
        val value = aRandomUniqueString()
        val ttl = aRandomTtl()
        every { upsertPropertyUseCase.execute(key, value, ttl) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            put("/properties/$key")
                .contentType("application/json")
                .authenticated()
                .content("""{"value": "$value", "ttl": $ttl}""")
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Entry for key '$key' upserted successfully"
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// postPropertyBatch ////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `postPropertyBatch() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/properties/batch")
            .contentType("application/json")
            .content("""[{"key": "${aRandomUniqueString()}", "value": "${aRandomUniqueString()}"}]""")
    }

    @Test fun `postPropertyBatch() returns 400 when entries list is empty`() {
        every { upsertPropertyBatchUseCase.execute(emptyList()) } returns EmptyPropertyBatch.left()

        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content("[]")
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "There are no entries to persist"
    }

    @Test fun `postPropertyBatch() returns 413 when the list has too many entries`() {
        val maxSize = UpsertPropertyBatchUseCase.MAX_BATCH_SIZE
        every { upsertPropertyBatchUseCase.execute(any()) } returns PropertyBatchTooLarge(maxSize).left()

        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(
                    objectMapper.writeValueAsString(
                        listOf(PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()))
                    )
                )
        )
            .andExpect(status().isPayloadTooLarge)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Batch size exceeds the maximum of $maxSize entries"
    }

    @Test fun `postPropertyBatch() returns 400 when the list has duplicates`() {
        every { upsertPropertyBatchUseCase.execute(any()) } returns DuplicatePropertyKeys.left()

        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(
                    objectMapper.writeValueAsString(
                        listOf(PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString()))
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Batch contains duplicate keys"
    }

    @Test fun `postPropertyBatch() returns 500 when fails to persist batch entries`() {
        val batchEntries = listOf(
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString())
        )
        every { upsertPropertyBatchUseCase.execute(batchEntries) } returns PropertyRepositoryError.left()

        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries))
        )
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to persist batch"
    }

    @Test fun `postPropertyBatch() returns 200 on success`() {
        val batchEntries = listOf(
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString(), aRandomTtl()),
            PropertyUpsertEntry(aRandomUniqueString(), aRandomUniqueString())
        )
        every { upsertPropertyBatchUseCase.execute(batchEntries) } returns Unit.right()

        val responseBody: String = mockMvc.perform(
            post("/properties/batch")
                .contentType("application/json")
                .authenticated()
                .content(objectMapper.writeValueAsString(batchEntries))
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Successfully persisted ${batchEntries.size} entries"
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// getPropertyEntry /////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getPropertyEntry() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/properties/${aRandomUniqueString()}")
    }

    @Test fun `getPropertyEntry() returns 500 when fails to get entry`() {
        val key = aRandomUniqueString()
        every { getPropertyUseCase.execute(key) } returns PropertyRepositoryError.left()

        val responseBody: String = mockMvc.perform(
            get("/properties/$key")
                .authenticated()
        )
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Failed to get property entry"
    }

    @Test fun `getPropertyEntry() returns 404 when key is not found`() {
        val key = aRandomUniqueString()
        every { getPropertyUseCase.execute(key) } returns PropertyNotFound.left()

        val responseBody: String = mockMvc.perform(
            get("/properties/$key")
                .authenticated()
        )
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Key '$key' is missing"
    }

    @Test fun `getPropertyEntry() returns 200 with entry on success`() {
        val key = aRandomUniqueString()
        val entry = PropertyEntry(value = aRandomUniqueString())
        every { getPropertyUseCase.execute(key) } returns entry.right()

        val responseBody: String = mockMvc.perform(
            get("/properties/$key")
                .authenticated()
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: PropertyEntry = objectMapper.readValue(responseBody, PropertyEntry::class.java)
        response shouldBe entry
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
