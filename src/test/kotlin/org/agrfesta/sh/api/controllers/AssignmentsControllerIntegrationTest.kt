package org.agrfesta.sh.api.controllers

import arrow.core.getOrElse
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.util.*
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.domain.anActuatorDataValue
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.DevicesRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AssignmentsControllerIntegrationTest(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository
): AbstractIntegrationTest() {
    private val uuid: UUID = UUID.randomUUID()

    @BeforeEach
    fun init() {
        every { randomGenerator.uuid() } returns uuid
    }

    @Test fun `assignSensorToArea() return 201 when successfully assigns device to area`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val deviceId = uuid
        devicesRepository.create(deviceId, aSensorDataValue()).getOrElse { error("Failed to create sensor: $it") }

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"areaId": "${area.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' successfully assigned to area with id '${area.uuid}'!"
    }

    @Test fun `assignActuatorToArea() return 201 when successfully assigns device to area`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val deviceId = uuid
        devicesRepository.create(deviceId, anActuatorDataValue()).getOrElse { error("Failed to create actuator: $it") }

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""{"areaId": "${area.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/actuators")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' successfully assigned to area with id '${area.uuid}'!"
    }

}
