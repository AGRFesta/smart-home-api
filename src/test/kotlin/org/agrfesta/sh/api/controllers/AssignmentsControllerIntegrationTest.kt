package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.utils.RandomGenerator
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
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.domain.anActuatorDataValue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AssignmentsControllerIntegrationTest(
    @Autowired @MockkBean private val randomGenerator: RandomGenerator,
    @Autowired private val areasDao: AreaDao,
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val sensorsAssignmentsDao: SensorsAssignmentsDao
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

        every { randomGenerator.uuid() } returns uuid
    }

    ///// assignSensorToArea ///////////////////////////////////////////////////////////////////////////////////////////
    @Test
    fun `assignSensorToArea() return 400 when area is not found`() {
        val deviceId = devicesDao.create(aSensorDataValue()).shouldBeRight()
        val areaId = UUID.randomUUID()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"areaId": "$areaId", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Area with id '$areaId' is missing!"
    }
    @Test
    fun `assignSensorToArea() return 400 when device is not found`() {
        val deviceId = UUID.randomUUID()
        val area = anArea()
        areasDao.save(area).shouldBeRight()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"areaId": "${area.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' is missing!"
    }
    @Test
    fun `assignSensorToArea() return 201 when successfully assigns device to area`() {
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val deviceId = devicesDao.create(aSensorDataValue()).shouldBeRight()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"areaId": "${area.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' successfully assigned to area with id '${area.uuid}'!"
    }

    @Test
    fun `assignSensorToArea() return 400 when device is already assigned to another area`() {
        val areaA = anArea()
        areasDao.save(areaA).shouldBeRight()
        val areaB = anArea()
        areasDao.save(areaB).shouldBeRight()
        val deviceId = devicesDao.create(aSensorDataValue()).shouldBeRight()
        sensorsAssignmentsDao.assign(areaId = areaA.uuid, sensorId = deviceId).shouldBeRight()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"areaId": "${areaB.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(400)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' is already assigned to another area!"
    }

    @Test
    fun `assignSensorToArea() return 400 when device is already assigned to this area`() {
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val deviceId = devicesDao.create(aSensorDataValue()).shouldBeRight()
        sensorsAssignmentsDao.assign(areaId = area.uuid, sensorId = deviceId).shouldBeRight()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"areaId": "${area.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(400)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' is already assigned to area with id '${area.uuid}'!"
    }

    @Test fun `assignSensorToArea() return 400 when device is not a sensor`() {
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val deviceId = devicesDao.create(anActuatorDataValue()).shouldBeRight()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"areaId": "${area.uuid}", "deviceId": "$deviceId"}""")
            .`when`()
            .post("/assignments/sensors")
            .then()
            .statusCode(400)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Device with id '$deviceId' is not a sensor!"
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
}
