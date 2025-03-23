package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.util.*
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class AreasControllerIntegrationTest(
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired private val areasDao: AreaDao
): AbstractIntegrationTest() {
    private val uuid: UUID = UUID.randomUUID()

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
        every { randomGenerator.uuid() } returns uuid
    }

    ///// create ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `create() return 201 when correctly create a area`() {
        val name = aRandomUniqueString()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "$name"}""")
            .`when`()
            .post("/areas")
            .then()
            .statusCode(201)
            .extract()
            .`as`(CreatedResourceResponse::class.java)

        result.message shouldBe "Area '$name' successfully created!"
        result.resourceId shouldBe uuid.toString()
        val expectedArea = Area(
            uuid = uuid,
            name = name,
            devices = emptyList(),
            isIndoor = true
        )
        areasDao.getAreaByName(name) shouldBe expectedArea
    }

    @Test fun `create() return 400 when create a area with an already existing name`() {
        val name = aRandomUniqueString()
        val area = anArea(name = name)
        areasDao.save(area)

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "$name"}""")
            .`when`()
            .post("/areas")
            .then()
            .statusCode(400)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "An Area '$name' already exists!"
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



}
