package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.util.*
import org.agrfesta.sh.api.core.application.ports.inbounds.AssignActuatorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.AssignSensorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.CreateAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAreaByIdUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAreasUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.UpdateAreaUseCase
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.NotASensor
import org.agrfesta.sh.api.core.domain.failures.NotAnActuator
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.security.SecurityConfig
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AreasController::class)
@Import(SecurityConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
class AreasControllerMvcSliceTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    @MockkBean private val createAreaUseCase: CreateAreaUseCase,
    @MockkBean private val getAreasUseCase: GetAreasUseCase,
    @MockkBean private val getAreaByIdUseCase: GetAreaByIdUseCase,
    @MockkBean private val deleteAreaUseCase: DeleteAreaUseCase,
    @MockkBean private val updateAreaUseCase: UpdateAreaUseCase,
    @MockkBean private val assignSensorToAreaUseCase: AssignSensorToAreaUseCase,
    @MockkBean private val assignActuatorToAreaUseCase: AssignActuatorToAreaUseCase
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
        every {
            createAreaUseCase.execute(name, null)
        } returns AreaDto(uuid = uuid, name = name, isIndoor = true).right()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: CreatedResourceResponse =
            objectMapper.readValue(responseBody, CreatedResourceResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
        response.resourceId shouldBe uuid.toString()
    }

    @Test fun `create() returns 201 when isIndoor is true`() {
        val name = aRandomUniqueString()
        val uuid = UUID.randomUUID()
        every {
            createAreaUseCase.execute(name, true)
        } returns AreaDto(uuid = uuid, name = name, isIndoor = true).right()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name", "isIndoor": true}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: CreatedResourceResponse =
            objectMapper.readValue(responseBody, CreatedResourceResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
        response.resourceId shouldBe uuid.toString()
    }

    @Test fun `create() returns 201 when isIndoor is false`() {
        val name = aRandomUniqueString()
        val uuid = UUID.randomUUID()
        every {
            createAreaUseCase.execute(name, false)
        } returns AreaDto(uuid = uuid, name = name, isIndoor = false).right()

        val responseBody: String = mockMvc.perform(
            post("/areas")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name", "isIndoor": false}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString

        val response: CreatedResourceResponse =
            objectMapper.readValue(responseBody, CreatedResourceResponse::class.java)
        response.message shouldBe "Area '$name' successfully created!"
        response.resourceId shouldBe uuid.toString()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// update ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `update() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/areas/${UUID.randomUUID()}")
            .contentType("application/json")
            .content("""{"name": "test", "isIndoor": true}""")
    }

    @Test fun `update() returns 500 on PersistenceFailure`() {
        val id = UUID.randomUUID()
        every { updateAreaUseCase.execute(id, any(), any()) } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$id")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "any", "isIndoor": true}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to update area '$id'!"
    }

    @Test fun `update() returns 400 on AreaNameConflict`() {
        val id = UUID.randomUUID()
        val name = aRandomUniqueString()
        every { updateAreaUseCase.execute(id, name, any()) } returns AreaNameConflict.left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$id")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$name", "isIndoor": true}"""))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "An Area '$name' already exists!"
    }

    @Test fun `update() returns 404 on AreaNotFound`() {
        val id = UUID.randomUUID()
        every { updateAreaUseCase.execute(id, any(), any()) } returns AreaNotFound(id).left()

        mockMvc.perform(
            put("/areas/$id")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "any", "isIndoor": true}"""))
            .andExpect(status().isNotFound)
    }

    @Test fun `update() returns 200 with updated area body on success`() {
        val area = anAreaDto()
        val newName = aRandomUniqueString()
        val updated = area.copy(name = newName, isIndoor = false)
        every { updateAreaUseCase.execute(area.uuid, newName, false) } returns updated.right()

        val responseBody: String = mockMvc.perform(
            put("/areas/${area.uuid}")
                .contentType("application/json")
                .authenticated()
                .content("""{"name": "$newName", "isIndoor": false}"""))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: Map<*, *> = objectMapper.readValue(responseBody, Map::class.java)
        withClue("uuid should match") { response["uuid"] shouldBe area.uuid.toString() }
        withClue("name should match") { response["name"] shouldBe newName }
        withClue("isIndoor should match") { response["isIndoor"] shouldBe false }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// delete ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `delete() auth tests`() = authTestSupport.dynamicTestsBy {
        delete("/areas/${UUID.randomUUID()}")
    }

    @Test fun `delete() returns 500 on PersistenceFailure`() {
        val id = UUID.randomUUID()
        every { deleteAreaUseCase.execute(id) } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            delete("/areas/$id").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to delete area '$id'!"
    }

    @Test fun `delete() returns 404 on AreaNotFound`() {
        val id = UUID.randomUUID()
        every { deleteAreaUseCase.execute(id) } returns AreaNotFound(id).left()

        mockMvc.perform(
            delete("/areas/$id").authenticated())
            .andExpect(status().isNotFound)
    }

    @Test fun `delete() returns 204 on success`() {
        val id = UUID.randomUUID()
        every { deleteAreaUseCase.execute(id) } returns Unit.right()

        mockMvc.perform(
            delete("/areas/$id").authenticated())
            .andExpect(status().isNoContent)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getById //////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getById() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/areas/${UUID.randomUUID()}")
    }

    @Test fun `getById() returns 500 on PersistenceFailure`() {
        val id = UUID.randomUUID()
        every { getAreaByIdUseCase.execute(id) } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            get("/areas/$id").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve area '$id'!"
    }

    @Test fun `getById() returns 404 on AreaNotFound`() {
        val id = UUID.randomUUID()
        every { getAreaByIdUseCase.execute(id) } returns AreaNotFound(id).left()

        mockMvc.perform(
            get("/areas/$id").authenticated())
            .andExpect(status().isNotFound)
    }

    @Test fun `getById() returns 200 with area body on success`() {
        val area = anAreaDto()
        every { getAreaByIdUseCase.execute(area.uuid) } returns area.right()

        val responseBody: String = mockMvc.perform(
            get("/areas/${area.uuid}").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: Map<*, *> = objectMapper.readValue(responseBody, Map::class.java)
        withClue("uuid should match") { response["uuid"] shouldBe area.uuid.toString() }
        withClue("name should match") { response["name"] shouldBe area.name }
        withClue("isIndoor should match") { response["isIndoor"] shouldBe area.isIndoor }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// getAll ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `getAll() auth tests`() = authTestSupport.dynamicTestsBy {
        get("/areas")
    }

    @Test fun `getAll() returns 200 with empty array when no areas exist`() {
        every { getAreasUseCase.execute() } returns emptyList<AreaDto>().right()

        val responseBody: String = mockMvc.perform(
            get("/areas").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: List<*> = objectMapper.readValue(responseBody, List::class.java)
        response shouldBe emptyList<Any>()
    }

    @Test fun `getAll() returns 500 on PersistenceFailure`() {
        every { getAreasUseCase.execute() } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            get("/areas").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to retrieve areas!"
    }

    @Test fun `getAll() returns 200 with areas array on success`() {
        val area1 = anAreaDto()
        val area2 = anAreaDto()
        every { getAreasUseCase.execute() } returns listOf(area1, area2).right()

        val responseBody: String = mockMvc.perform(
            get("/areas").authenticated())
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val response: List<Map<String, Any>> =
            objectMapper.readValue(responseBody, objectMapper.typeFactory
                .constructCollectionType(List::class.java, Map::class.java))
        withClue("response should contain both areas") {
            response.map { it["uuid"] } shouldContainExactlyInAnyOrder
                listOf(area1.uuid.toString(), area2.uuid.toString())
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// assignSensorToArea //////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `assignSensorToArea() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/areas/${UUID.randomUUID()}/sensors/${UUID.randomUUID()}")
    }

    @Test fun `assignSensorToArea() returns 404 when device is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignSensorToAreaUseCase.execute(areaId, deviceId) } returns DeviceNotFound(deviceId).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/sensors/$deviceId").authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is missing!"
    }

    @Test fun `assignSensorToArea() returns 204 when sensor is already assigned to the same area`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignSensorToAreaUseCase.execute(areaId, deviceId) } returns SameAreaAssignment.left()

        mockMvc.perform(
            put("/areas/$areaId/sensors/$deviceId").authenticated())
            .andExpect(status().isNoContent)
    }

    @Test fun `assignSensorToArea() returns 400 when sensor is already assigned to another area`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignSensorToAreaUseCase.execute(areaId, deviceId) } returns SensorAlreadyAssigned.left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/sensors/$deviceId").authenticated())
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is already assigned to another area!"
    }

    @Test fun `assignSensorToArea() returns 400 when device is not a sensor`() {
        val areaId = UUID.randomUUID()
        val device = anActuator()
        every { assignSensorToAreaUseCase.execute(areaId, device.uuid) } returns
                NotASensor(device.uuid, device.features).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/sensors/${device.uuid}").authenticated())
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '${device.uuid}' is not a sensor!"
    }

    @Test fun `assignSensorToArea() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignSensorToAreaUseCase.execute(areaId, deviceId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/sensors/$deviceId").authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `assignSensorToArea() returns 500 on PersistenceFailure`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignSensorToAreaUseCase.execute(areaId, deviceId) } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/sensors/$deviceId").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign sensor '$deviceId' to area '$areaId'!"
    }

    @Test fun `assignSensorToArea() returns 204 on success`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignSensorToAreaUseCase.execute(areaId, deviceId) } returns Unit.right()

        mockMvc.perform(
            put("/areas/$areaId/sensors/$deviceId").authenticated())
            .andExpect(status().isNoContent)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ///// assignActuatorToArea ////////////////////////////////////////////////////////////////////////////////////////

    @TestFactory fun `assignActuatorToArea() auth tests`() = authTestSupport.dynamicTestsBy {
        put("/areas/${UUID.randomUUID()}/actuators/${UUID.randomUUID()}")
    }

    @Test fun `assignActuatorToArea() returns 400 when device is not an actuator`() {
        val areaId = UUID.randomUUID()
        val device = aSensor()
        every { assignActuatorToAreaUseCase.execute(areaId, device.uuid) } returns
                NotAnActuator(device.uuid, device.features).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/actuators/${device.uuid}").authenticated())
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '${device.uuid}' is not an actuator!"
    }

    @Test fun `assignActuatorToArea() returns 404 when device is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignActuatorToAreaUseCase.execute(areaId, deviceId) } returns DeviceNotFound(deviceId).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/actuators/$deviceId").authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Device with id '$deviceId' is missing!"
    }

    @Test fun `assignActuatorToArea() returns 204 when actuator is already assigned to the same area`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignActuatorToAreaUseCase.execute(areaId, deviceId) } returns SameAreaAssignment.left()

        mockMvc.perform(
            put("/areas/$areaId/actuators/$deviceId").authenticated())
            .andExpect(status().isNoContent)
    }

    @Test fun `assignActuatorToArea() returns 404 when area is not found`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignActuatorToAreaUseCase.execute(areaId, deviceId) } returns AreaNotFound(areaId).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/actuators/$deviceId").authenticated())
            .andExpect(status().isNotFound)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Area with id '$areaId' is missing!"
    }

    @Test fun `assignActuatorToArea() returns 500 on PersistenceFailure`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignActuatorToAreaUseCase.execute(areaId, deviceId) } returns
                PersistenceFailure(RuntimeException("db error")).left()

        val responseBody: String = mockMvc.perform(
            put("/areas/$areaId/actuators/$deviceId").authenticated())
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to assign actuator '$deviceId' to area '$areaId'!"
    }

    @Test fun `assignActuatorToArea() returns 204 on success`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { assignActuatorToAreaUseCase.execute(areaId, deviceId) } returns Unit.right()

        mockMvc.perform(
            put("/areas/$areaId/actuators/$deviceId").authenticated())
            .andExpect(status().isNoContent)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
