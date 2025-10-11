package org.agrfesta.sh.api.controllers

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import java.math.BigDecimal
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.services.AssignmentsService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class HomeStatusControllerIntegrationTest(
    @Autowired private val cache: SmartCache,
    @Autowired private val areasDao: AreaDao,
    @Autowired private val areasJdbcRepo: AreasJdbcRepository,
    @Autowired private val devicesService: DevicesService,
    @Autowired private val devicesJdbcRepository: DevicesJdbcRepository,
    @Autowired private val assignmentsService: AssignmentsService,
    @Autowired private val sensorsAssignmentsJdbcRepository: SensorsAssignmentsJdbcRepository
): AbstractIntegrationTest() {

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
        sensorsAssignmentsJdbcRepository.deleteAll()
        areasJdbcRepo.deleteAll()
        devicesJdbcRepository.deleteAll()
    }

    @Test fun `getHomeStatus() returns an empty collection when there are no areas`() {
        val areaStatuses: Collection<AreaStatusView> = given()
            .authenticated()
            .`when`()
            .get("/home/status")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .`as`(object : TypeRef<Collection<AreaStatusView>>() {})

        areaStatuses.shouldBeEmpty()
    }

    @Test fun `getHomeStatus() returns areas status`() {
        val areaA = anArea()
        areasDao.save(areaA)
        val areaB = anArea()
        areasDao.save(areaB)
        val areaC = anArea()
        areasDao.save(areaC)

        val areaStatuses: Collection<AreaStatusView> = given()
            .authenticated()
            .`when`()
            .get("/home/status")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .`as`(object : TypeRef<Collection<AreaStatusView>>() {})

        areaStatuses.map { listOf(it.id, it.name, it.temperature) }.shouldContainExactlyInAnyOrder(
            listOf(areaB.uuid, areaB.name, null),
            listOf(areaC.uuid, areaC.name, null),
            listOf(areaA.uuid, areaA.name, null)
        )
    }

    @Test fun `getHomeStatus() returns areas status with temperature average if exist`() {
        // Setup Area A
        val areaA = anArea()
        areasDao.save(areaA)
        val sensorA0Data = aSensorDataValue()
        val sensorA0Id = devicesService.createDevice(sensorA0Data).shouldBeRight()
        val sensorA1Data = aSensorDataValue()
        val sensorA1Id = devicesService.createDevice(sensorA1Data).shouldBeRight()
        val sensorA2Data = aSensorDataValue()
        val sensorA2Id = devicesService.createDevice(sensorA2Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA0Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA1Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA2Id).shouldBeRight()
        cache.setThermoHygroOf(sensorA0Data, aRandomThermoHygroData(temperature = BigDecimal("20.5")))
        cache.setThermoHygroOf(sensorA1Data, aRandomThermoHygroData(temperature = BigDecimal("22")))
        cache.setThermoHygroOf(sensorA2Data, aRandomThermoHygroData(temperature = BigDecimal("22")))
        // Setup Area B
        val areaB = anArea()
        areasDao.save(areaB)
        // Setup Area C
        val areaC = anArea()
        areasDao.save(areaC)
        val sensorC0Data = aSensorDataValue()
        val sensorC0Id = devicesService.createDevice(sensorC0Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaC.uuid, sensorC0Id).shouldBeRight()
        cache.setThermoHygroOf(sensorC0Data, aRandomThermoHygroData(temperature = BigDecimal("30")))

        val areaStatuses: Collection<AreaStatusView> = given()
            .authenticated()
            .`when`()
            .get("/home/status")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .`as`(object : TypeRef<Collection<AreaStatusView>>() {})

        areaStatuses.map { listOf(it.id, it.name, it.temperature) }.shouldContainExactlyInAnyOrder(
            listOf(areaB.uuid, areaB.name, null),
            listOf(areaC.uuid, areaC.name, BigDecimal("30")),
            listOf(areaA.uuid, areaA.name, BigDecimal("21.5"))
        )
    }

    @Test fun `getHomeStatus() ignores missing sensor temperature in cache`() {
        // Setup Area A
        val areaA = anArea()
        areasDao.save(areaA)
        val sensorA0Data = aSensorDataValue()
        val sensorA0Id = devicesService.createDevice(sensorA0Data).shouldBeRight()
        val sensorA1Data = aSensorDataValue()
        val sensorA1Id = devicesService.createDevice(sensorA1Data).shouldBeRight()
        val sensorA2Data = aSensorDataValue()
        val sensorA2Id = devicesService.createDevice(sensorA2Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA0Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA1Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA2Id).shouldBeRight()
        cache.setThermoHygroOf(sensorA1Data, aRandomThermoHygroData(temperature = BigDecimal("20")))
        cache.setThermoHygroOf(sensorA2Data, aRandomThermoHygroData(temperature = BigDecimal("22")))
        // Setup Area B
        val areaB = anArea()
        areasDao.save(areaB)
        // Setup Area C
        val areaC = anArea()
        areasDao.save(areaC)
        val sensorC0Data = aSensorDataValue()
        val sensorC0Id = devicesService.createDevice(sensorC0Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaC.uuid, sensorC0Id).shouldBeRight()

        val areaStatuses: Collection<AreaStatusView> = given()
            .authenticated()
            .`when`()
            .get("/home/status")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .`as`(object : TypeRef<Collection<AreaStatusView>>() {})

        areaStatuses.map { listOf(it.id, it.name, it.temperature) }.shouldContainExactlyInAnyOrder(
            listOf(areaB.uuid, areaB.name, null),
            listOf(areaC.uuid, areaC.name, null),
            listOf(areaA.uuid, areaA.name, BigDecimal("21"))
        )
    }

}
