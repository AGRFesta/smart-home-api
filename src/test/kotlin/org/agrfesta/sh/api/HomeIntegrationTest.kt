package org.agrfesta.sh.api

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.agrfesta.sh.api.services.AssignmentsService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.heating.DynamicSharedHeatingStrategyService.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.services.heating.HeatingOrchestrationService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test

class HomeIntegrationTest(
    private val areasRepository: AreasRepository,
    private val devicesService: DevicesService,
    private val assignmentsService: AssignmentsService,
    private val smartCache: SmartCache,
    private val propertyRepository: PropertyJdbcRepository
): AbstractIntegrationTest() {

    ///// getHome //////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `getHome() returns area measurements populated from sensor readings in Redis`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val sensorData = aSensorProviderData()
        val sensorId = devicesService.createDevice(sensorData).shouldBeRight()
        assignmentsService.assignSensorToArea(area.uuid, sensorId).shouldBeRight()
        val readings = aRandomThermoHygroData()
        smartCache.setThermoHygroOf(sensorData, readings)

        val json = given()
            .authenticated()
            .`when`()
            .get("/home")
            .then()
            .statusCode(200)
            .extract().body().jsonPath()

        withClue("one area returned") { json.getList<Any>("areas").size shouldBe 1 }
        withClue("area id and name") {
            json.getString("areas[0].id") shouldBe area.uuid.toString()
            json.getString("areas[0].name") shouldBe area.name
        }
        withClue("currentTemperature is resolved from cache") {
            json.getString("areas[0].measurements.heating.currentTemperature.type") shouldBe "success"
            json.get<Any?>("areas[0].measurements.heating.currentTemperature.value").shouldNotBeNull()
        }
        withClue("relative humidity is resolved from cache") {
            json.getString("areas[0].measurements.humidity.relative.type") shouldBe "success"
            json.get<Any?>("areas[0].measurements.humidity.relative.value").shouldNotBeNull()
        }
    }

    @Test fun `getHome() reflects heating properties stored in PostgreSQL`() {
        propertyRepository.upsert(HEATING_ENABLED_KEY, "true")
        propertyRepository.upsert(HEATING_STRATEGY_KEY, "COMFORT")

        val json = given()
            .authenticated()
            .`when`()
            .get("/home")
            .then()
            .statusCode(200)
            .extract().body().jsonPath()

        withClue("heatingActive is true") {
            json.getString("globalState.heatingActive.type") shouldBe "success"
            json.getBoolean("globalState.heatingActive.value") shouldBe true
        }
        withClue("strategy is COMFORT") {
            json.getString("globalState.strategy.type") shouldBe "success"
            json.getString("globalState.strategy.value") shouldBe "COMFORT"
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
