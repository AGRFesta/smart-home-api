package org.agrfesta.sh.api

import arrow.core.getOrElse
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.CreatedResourceResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.domain.anActuatorProviderData
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class AreasIntegrationTest(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository,
    private val actuatorsAssignmentsRepository: ActuatorsAssignmentsRepository
) : AbstractIntegrationTest() {
    private val uuid: UUID = UUID.randomUUID()

    @BeforeEach
    fun init() {
        every { randomGenerator.uuid() } returns uuid
    }

    // /// create ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `create() return 201 when correctly create a area`() {
        val name = aRandomUniqueString()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"name": "$name"}""")
            .`when`()
            .post("/areas")
            .then()
            .statusCode(201)
            .extract()
            .`as`(CreatedResourceResponse::class.java)

        result.message shouldBe "Area '$name' successfully created!"
        result.resourceId shouldBe uuid.toString()
        val expectedArea = AreaDto(
            uuid = uuid,
            name = name,
            isIndoor = true
        )
        areasRepository.findAreaByName(name).getOrNull() shouldBe expectedArea
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// getById //////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getById() returns 200 with the correct area`() {
        val area = anAreaDto()
        areasRepository.save(area)

        val response: Map<String, Any> = given()
            .authenticated()
            .`when`()
            .get("/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap(".")

        withClue("uuid should match") { response["uuid"] shouldBe area.uuid.toString() }
        withClue("name should match") { response["name"] shouldBe area.name }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// delete ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `delete() returns 204 and area is no longer retrievable`() {
        val area = anAreaDto()
        areasRepository.save(area)

        given()
            .authenticated()
            .`when`()
            .delete("/areas/${area.uuid}")
            .then()
            .statusCode(204)

        given()
            .authenticated()
            .`when`()
            .get("/areas/${area.uuid}")
            .then()
            .statusCode(404)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// getAll ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getAll() returns 200 with persisted areas`() {
        val area1 = anAreaDto()
        val area2 = anAreaDto()
        areasRepository.save(area1)
        areasRepository.save(area2)

        val uuids: List<String> = given()
            .authenticated()
            .`when`()
            .get("/areas")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("uuid", String::class.java)

        withClue("response should contain both persisted areas") {
            uuids shouldContainExactlyInAnyOrder listOf(area1.uuid.toString(), area2.uuid.toString())
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// update ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `update() returns 200 and area is updated in DB`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val newName = aRandomUniqueString()

        val response: Map<String, Any> = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"name": "$newName", "isIndoor": false}""")
            .`when`()
            .put("/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap(".")

        withClue("uuid should be unchanged") { response["uuid"] shouldBe area.uuid.toString() }
        withClue("name should be updated") { response["name"] shouldBe newName }
        withClue("isIndoor should be updated") { response["isIndoor"] shouldBe false }
        val persisted = areasRepository.getAreaById(area.uuid).getOrNull()
        withClue("DB should reflect new name") { persisted?.name shouldBe newName }
        withClue("DB should reflect new isIndoor") { persisted?.isIndoor shouldBe false }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /// assignSensorToArea //////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `assignSensorToArea() returns 204 on success`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val deviceId = uuid
        devicesRepository.create(deviceId, aSensorProviderData()).getOrElse { error("Failed to create sensor: $it") }

        given()
            .authenticated()
            .`when`()
            .put("/areas/${area.uuid}/sensors/$deviceId")
            .then()
            .statusCode(204)
    }

    // /// unassignSensorFromArea //////////////////////////////////////////////////////////////////////////////////////

    @Test fun `unassignSensorFromArea() returns 204 on success`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val deviceId = uuid
        devicesRepository.create(deviceId, aSensorProviderData()).getOrElse { error("Failed to create sensor: $it") }
        sensorsAssignmentsRepository.assign(area.uuid, deviceId).getOrElse { error("Failed to assign sensor: $it") }

        given()
            .authenticated()
            .`when`()
            .delete("/areas/${area.uuid}/sensors/$deviceId")
            .then()
            .statusCode(204)
    }

    // /// unassignActuatorFromArea ////////////////////////////////////////////////////////////////////////////////////

    @Test fun `unassignActuatorFromArea() returns 204 on success`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val deviceId = uuid
        devicesRepository.create(
            deviceId,
            anActuatorProviderData()
        ).getOrElse { error("Failed to create actuator: $it") }
        actuatorsAssignmentsRepository.assign(area.uuid, deviceId).getOrElse { error("Failed to assign actuator: $it") }

        given()
            .authenticated()
            .`when`()
            .delete("/areas/${area.uuid}/actuators/$deviceId")
            .then()
            .statusCode(204)
    }

    // /// assignActuatorToArea ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `assignActuatorToArea() returns 204 on success`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val deviceId = uuid
        devicesRepository.create(deviceId, anActuatorProviderData())
            .getOrElse { error("Failed to create actuator: $it") }

        given()
            .authenticated()
            .`when`()
            .put("/areas/${area.uuid}/actuators/$deviceId")
            .then()
            .statusCode(204)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
