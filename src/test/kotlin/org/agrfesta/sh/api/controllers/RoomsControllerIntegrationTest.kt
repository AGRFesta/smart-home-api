package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.AssociationsDao
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.RoomsDao
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
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RoomsControllerIntegrationTest(
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired private val roomsDao: RoomsDao,
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val associationsDao: AssociationsDao
) {

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

    }

    @LocalServerPort private val port: Int? = null

    private val uuid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
        //roomsRepository.deleteAll()

        every { randomGenerator.uuid() } returns uuid
    }

    ///// create ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `create() return 201 when correctly create a room`() {
        val name = aRandomUniqueString()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "$name"}""")
            .`when`()
            .post("/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(CreatedResourceResponse::class.java)

        result.message shouldBe "Room '$name' successfully created!"
        result.resourceId shouldBe uuid.toString()
        val expectedRoom = Room(
            uuid = uuid,
            name = name,
            devices = emptyList()
        )
        roomsDao.getRoomByName(name).shouldBeRight(expectedRoom)
    }

    @Test fun `create() return 400 when create a room with an already existing name`() {
        val name = aRandomUniqueString()
        val room = Room(
            uuid = UUID.randomUUID(),
            name = name,
            devices = emptyList()
        )
        roomsDao.save(room).shouldBeRight()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"name": "$name"}""")
            .`when`()
            .post("/rooms")
            .then()
            .statusCode(400)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "A Room '$name' already exists!"
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



}
