package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.persistence.AssociationsDao
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.test.mothers.aRandomUniqueString
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
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AreasControllerIntegrationTest(
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired private val areasDao: AreaDao,
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val associationsDao: AssociationsDao
) {

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("timescale/timescaledb:latest-pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }

    }

    @LocalServerPort private val port: Int? = null

    private val uuid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
        //areasRepository.deleteAll()

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
        areasDao.getAreaByName(name).shouldBeRight(expectedArea)
    }

    @Test fun `create() return 400 when create a area with an already existing name`() {
        val name = aRandomUniqueString()
        val area = anArea(name = name)
        areasDao.save(area).shouldBeRight()

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
