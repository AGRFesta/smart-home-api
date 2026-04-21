package org.agrfesta.sh.api.controllers

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.util.*
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AreasControllerIntegrationTest(
    private val areasRepository: AreasRepository
): AbstractIntegrationTest() {
    private val uuid: UUID = UUID.randomUUID()

    @BeforeEach
    fun init() {
        every { randomGenerator.uuid() } returns uuid
    }

    ///// create ///////////////////////////////////////////////////////////////////////////////////////////////////////

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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



}
