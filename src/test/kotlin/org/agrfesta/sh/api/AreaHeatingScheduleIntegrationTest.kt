package org.agrfesta.sh.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.time.LocalTime
import org.agrfesta.sh.api.controllers.HeatingScheduleResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test

class AreaHeatingScheduleIntegrationTest(
    private val areasRepository: AreasRepository,
    private val temperatureSettingsRepository: TemperatureSettingsRepository
) : AbstractIntegrationTest() {

    ///// GET /areas/{areaId}/heating-schedule /////////////////////////////////////////////////////////////////////////

    @Test fun `getHeatingSchedule() returns 200 with schedule when area has a heating schedule`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val defaultTemperature = aRandomTemperature()
        temperatureSettingsRepository.createSetting(anAreaTemperatureSetting(
            areaId = area.uuid,
            defaultTemperature = defaultTemperature,
            temperatureSchedule = setOf(
                aTemperatureInterval(startTime = aDailyTime(hour = 8), endTime = aDailyTime(hour = 10)),
                aTemperatureInterval(startTime = aDailyTime(hour = 12), endTime = aDailyTime(hour = 14))
            )
        ))

        val response = given()
            .authenticated()
            .`when`()
            .get("/areas/${area.uuid}/heating-schedule")
            .then()
            .statusCode(200)
            .extract()
            .`as`(HeatingScheduleResponse::class.java)

        response.defaultTemperature shouldBe defaultTemperature
        response.intervals shouldHaveSize 2
    }

    @Test fun `getHeatingSchedule() returns 200 with default empty structure when area has no schedule`() {
        val area = anAreaDto()
        areasRepository.save(area)

        val response = given()
            .authenticated()
            .`when`()
            .get("/areas/${area.uuid}/heating-schedule")
            .then()
            .statusCode(200)
            .extract()
            .`as`(HeatingScheduleResponse::class.java)

        response.defaultTemperature.shouldNotBeNull()
        response.intervals shouldHaveSize 0
    }

    ///// PUT /areas/{areaId}/heating-schedule /////////////////////////////////////////////////////////////////////////

    @Test fun `replaceHeatingSchedule() creates schedule and returns 200 with interval IDs`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val defaultTemperature = aRandomTemperature()
        val intA = TemperatureInterval(aRandomTemperature(), LocalTime.of(8, 0), LocalTime.of(10, 0))
        val intB = TemperatureInterval(aRandomTemperature(), LocalTime.of(12, 0), LocalTime.of(14, 0))

        val response = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""
                {
                    "defaultTemperature": ${defaultTemperature.value},
                    "intervals": [
                        {"temperature": ${intA.temperature.value}, "startTime": "08:00", "endTime": "10:00"},
                        {"temperature": ${intB.temperature.value}, "startTime": "12:00", "endTime": "14:00"}
                    ]
                }
            """.trimIndent())
            .`when`()
            .put("/areas/${area.uuid}/heating-schedule")
            .then()
            .statusCode(200)
            .extract()
            .`as`(HeatingScheduleResponse::class.java)

        response.defaultTemperature shouldBe defaultTemperature
        response.intervals shouldHaveSize 2

        val saved = temperatureSettingsRepository.findAreaSetting(area.uuid).getOrNull().shouldNotBeNull()
        saved.defaultTemperature shouldBe defaultTemperature
        saved.temperatureSchedule shouldHaveSize 2
    }

    @Test fun `replaceHeatingSchedule() replaces existing schedule`() {
        val area = anAreaDto()
        areasRepository.save(area)
        temperatureSettingsRepository.createSetting(anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(
                aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 1)),
                aTemperatureInterval(startTime = aDailyTime(hour = 2), endTime = aDailyTime(hour = 3))
            )
        ))
        val newDefaultTemperature = aRandomTemperature()

        val response = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body("""
                {
                    "defaultTemperature": ${newDefaultTemperature.value},
                    "intervals": []
                }
            """.trimIndent())
            .`when`()
            .put("/areas/${area.uuid}/heating-schedule")
            .then()
            .statusCode(200)
            .extract()
            .`as`(HeatingScheduleResponse::class.java)

        response.defaultTemperature shouldBe newDefaultTemperature
        response.intervals shouldHaveSize 0

        val saved = temperatureSettingsRepository.findAreaSetting(area.uuid).getOrNull().shouldNotBeNull()
        saved.defaultTemperature shouldBe newDefaultTemperature
        saved.temperatureSchedule shouldHaveSize 0
    }

    ///// DELETE /areas/{areaId}/heating-schedule //////////////////////////////////////////////////////////////////////

    @Test fun `deleteHeatingSchedule() deletes schedule and returns 204`() {
        val area = anAreaDto()
        areasRepository.save(area)
        temperatureSettingsRepository.createSetting(anAreaTemperatureSetting(areaId = area.uuid))

        given()
            .authenticated()
            .`when`()
            .delete("/areas/${area.uuid}/heating-schedule")
            .then()
            .statusCode(204)

        temperatureSettingsRepository.findAreaSetting(area.uuid).getOrNull().shouldBe(null)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
