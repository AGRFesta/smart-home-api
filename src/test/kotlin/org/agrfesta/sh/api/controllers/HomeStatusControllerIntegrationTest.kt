package org.agrfesta.sh.api.controllers

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import java.math.BigDecimal
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.services.AssignmentsService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeStatusControllerIntegrationTest(
    private val cache: SmartCache,
    private val areasRepository: AreasRepository,
    private val areasJdbcRepo: AreasJdbcRepository,
    private val devicesService: DevicesService,
    private val devicesJdbcRepository: DevicesJdbcRepository,
    private val assignmentsService: AssignmentsService,
    private val sensorsAssignmentsJdbcRepository: SensorsAssignmentsJdbcRepository
): AbstractIntegrationTest() {

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
        val areaA = anAreaDto()
        areasRepository.save(areaA)
        val areaB = anAreaDto()
        areasRepository.save(areaB)
        val areaC = anAreaDto()
        areasRepository.save(areaC)

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
        val areaA = anAreaDto()
        areasRepository.save(areaA)
        val sensorA0Data = aSensorDataValue()
        val sensorA0Id = devicesService.createDevice(sensorA0Data).shouldBeRight()
        val sensorA1Data = aSensorDataValue()
        val sensorA1Id = devicesService.createDevice(sensorA1Data).shouldBeRight()
        val sensorA2Data = aSensorDataValue()
        val sensorA2Id = devicesService.createDevice(sensorA2Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA0Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA1Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA2Id).shouldBeRight()
        cache.setThermoHygroOf(sensorA0Data, aRandomThermoHygroData(temperature = Temperature.of("20.5")))
        cache.setThermoHygroOf(sensorA1Data, aRandomThermoHygroData(temperature = Temperature.of("22")))
        cache.setThermoHygroOf(sensorA2Data, aRandomThermoHygroData(temperature = Temperature.of("22")))
        // Setup Area B
        val areaB = anAreaDto()
        areasRepository.save(areaB)
        // Setup Area C
        val areaC = anAreaDto()
        areasRepository.save(areaC)
        val sensorC0Data = aSensorDataValue()
        val sensorC0Id = devicesService.createDevice(sensorC0Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaC.uuid, sensorC0Id).shouldBeRight()
        cache.setThermoHygroOf(sensorC0Data, aRandomThermoHygroData(temperature = Temperature.of("30")))

        val areaStatuses: Collection<AreaStatusView> = given()
            .authenticated()
            .`when`()
            .get("/home/status")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .`as`(object : TypeRef<Collection<AreaStatusView>>() {})

        areaStatuses.map { listOf(it.id, it.name, it.temperature?.value) }.shouldContainExactlyInAnyOrder(
            listOf(areaB.uuid, areaB.name, null),
            listOf(areaC.uuid, areaC.name, BigDecimal("30")),
            listOf(areaA.uuid, areaA.name, BigDecimal("21.5"))
        )
    }

    @Test fun `getHomeStatus() ignores missing sensor temperature in cache`() {
        // Setup Area A
        val areaA = anAreaDto()
        areasRepository.save(areaA)
        val sensorA0Data = aSensorDataValue()
        val sensorA0Id = devicesService.createDevice(sensorA0Data).shouldBeRight()
        val sensorA1Data = aSensorDataValue()
        val sensorA1Id = devicesService.createDevice(sensorA1Data).shouldBeRight()
        val sensorA2Data = aSensorDataValue()
        val sensorA2Id = devicesService.createDevice(sensorA2Data).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA0Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA1Id).shouldBeRight()
        assignmentsService.assignSensorToArea(areaA.uuid, sensorA2Id).shouldBeRight()
        cache.setThermoHygroOf(sensorA1Data, aRandomThermoHygroData(temperature = Temperature.of("20")))
        cache.setThermoHygroOf(sensorA2Data, aRandomThermoHygroData(temperature = Temperature.of("22")))
        // Setup Area B
        val areaB = anAreaDto()
        areasRepository.save(areaB)
        // Setup Area C
        val areaC = anAreaDto()
        areasRepository.save(areaC)
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

        areaStatuses.map { listOf(it.id, it.name, it.temperature?.value) }.shouldContainExactlyInAnyOrder(
            listOf(areaB.uuid, areaB.name, null),
            listOf(areaC.uuid, areaC.name, null),
            listOf(areaA.uuid, areaA.name, Temperature.of("21").value)
        )
    }

}
