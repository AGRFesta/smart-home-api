package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.util.*
import org.agrfesta.sh.api.core.application.ports.inbounds.CreateAreaUseCase
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AreasController::class)
@Import(SecurityConfig::class)
@ActiveProfiles("test")
class AreasControllerMvcSliceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val createAreaUseCase: CreateAreaUseCase
) {
    private val authTestSupport = AuthTestSupport(mockMvc, objectMapper)

    ///// create ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `create() auth tests`() = authTestSupport.dynamicTestsBy {
        post("/areas")
            .contentType("application/json")
            .content("""{"name": "${aRandomUniqueString()}"}""")
    }

    @Test fun `create() returns 400 when area name already exists`() {
        val name = aRandomUniqueString()
        every { createAreaUseCase.execute(name, null) } returns AreaNameConflict.left()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name"}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "An Area '$name' already exists!"
    }

    @Test fun `create() returns 500 when persistence fails`() {
        val name = aRandomUniqueString()
        every { createAreaUseCase.execute(name, null) } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name"}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create Area '$name'!"
    }

    @Test fun `create() returns 201 with resource id on success`() {
        val name = aRandomUniqueString()
        val uuid = UUID.randomUUID()
        every { createAreaUseCase.execute(name, null) } returns AreaDto(uuid = uuid, name = name, isIndoor = true).right()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: CreatedResourceResponse = objectMapper.readValue(responseBody, CreatedResourceResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
        response.resourceId shouldBe uuid.toString()
    }

    @Test fun `create() returns 201 when isIndoor is true`() {
        val name = aRandomUniqueString()
        val uuid = UUID.randomUUID()
        every { createAreaUseCase.execute(name, true) } returns AreaDto(uuid = uuid, name = name, isIndoor = true).right()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name", "isIndoor": true}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: CreatedResourceResponse = objectMapper.readValue(responseBody, CreatedResourceResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
        response.resourceId shouldBe uuid.toString()
    }

    @Test fun `create() returns 201 when isIndoor is false`() {
        val name = aRandomUniqueString()
        val uuid = UUID.randomUUID()
        every { createAreaUseCase.execute(name, false) } returns AreaDto(uuid = uuid, name = name, isIndoor = false).right()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name", "isIndoor": false}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: CreatedResourceResponse = objectMapper.readValue(responseBody, CreatedResourceResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
        response.resourceId shouldBe uuid.toString()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
