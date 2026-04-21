package org.agrfesta.sh.api.controllers

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TemperatureSettingsRepository
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test

class HeatingAreasControllerIntegrationTest(
    private val areasRepository: AreasRepository,
    private val tempSettingsDao: TemperatureSettingsRepository
): AbstractIntegrationTest() {

    ///// createHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createHeatingSchedule() then getHeatingSchedule() then deleteHeatingSchedule() full lifecycle`() {
        val area = anAreaDto()
        areasRepository.save(area)
        val defaultTemperature = aRandomTemperature()
        val tempIntA = aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 1))
        val tempIntB = aTemperatureInterval(startTime = aDailyTime(hour = 2), endTime = aDailyTime(hour = 3))
        val tempIntC = aTemperatureInterval(startTime = aDailyTime(hour = 4), endTime = aDailyTime(hour = 5))
        val body = """
            {
                "defaultTemperature": ${defaultTemperature.value},
                "temperatureSchedule": [
                    ${tempIntA.toJson()},
                    ${tempIntB.toJson()},
                    ${tempIntC.toJson()}
                ]
            }
        """.trimIndent()

        // POST → 201, verify DB
        given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body(body)
            .`when`()
            .post("/heating/areas/${area.uuid}")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)
            .message shouldBe "Successfully created heating schedule for area with id '${area.uuid}'!"
        val savedSetting = tempSettingsDao.findAreaSetting(area.uuid).getOrNull().shouldNotBeNull()
        savedSetting.defaultTemperature shouldBe defaultTemperature
        savedSetting.temperatureSchedule.shouldContainExactlyInAnyOrder(tempIntA, tempIntB, tempIntC)

        // GET → 200, verify DTO
        val getResult = given()
            .authenticated()
            .`when`()
            .get("/heating/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .`as`(TemperatureSettings::class.java)
        getResult.defaultTemperature shouldBe defaultTemperature
        getResult.temperatureSchedule.shouldContainExactlyInAnyOrder(tempIntA, tempIntB, tempIntC)

        // DELETE → 200, verify DB
        given()
            .authenticated()
            .`when`()
            .delete("/heating/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)
            .message shouldBe "Successfully deleted heating schedule for area with id '${area.uuid}'!"
        tempSettingsDao.findAreaSetting(area.uuid).getOrNull().shouldBeNull()

        // GET after delete → 204 (area exists, setting gone)
        given()
            .authenticated()
            .`when`()
            .get("/heating/areas/${area.uuid}")
            .then()
            .statusCode(204)
    }

    @Test fun `createHeatingSchedule() replaces existing setting`() {
        val area = anAreaDto()
        areasRepository.save(area)
        tempSettingsDao.createSetting(anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(aTemperatureInterval())
        ))
        val newDefaultTemperature = aRandomTemperature()
        val tempIntA = aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 1))
        val tempIntB = aTemperatureInterval(startTime = aDailyTime(hour = 2), endTime = aDailyTime(hour = 3))
        val tempIntC = aTemperatureInterval(startTime = aDailyTime(hour = 4), endTime = aDailyTime(hour = 5))
        val body = """
            {
                "defaultTemperature": ${newDefaultTemperature.value},
                "temperatureSchedule": [
                    ${tempIntA.toJson()},
                    ${tempIntB.toJson()},
                    ${tempIntC.toJson()}
                ]
            }
        """.trimIndent()

        // POST (replace) → 201
        given()
            .contentType(ContentType.JSON)
            .authenticated()
            .body(body)
            .`when`()
            .post("/heating/areas/${area.uuid}")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)
            .message shouldBe "Successfully created heating schedule for area with id '${area.uuid}'!"

        // GET → 200, verify only the new setting exists
        val result = given()
            .authenticated()
            .`when`()
            .get("/heating/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .`as`(TemperatureSettings::class.java)
        result.defaultTemperature shouldBe newDefaultTemperature
        result.temperatureSchedule.shouldContainExactlyInAnyOrder(tempIntA, tempIntB, tempIntC)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// deleteHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `deleteHeatingSchedule() returns 200 when area has no setting`() {
        val area = anAreaDto()
        areasRepository.save(area)

        given()
            .authenticated()
            .`when`()
            .delete("/heating/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)
            .message shouldBe "Successfully deleted heating schedule for area with id '${area.uuid}'!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun TemperatureInterval.toJson() = """
            {
                "temperature": ${temperature.value},
                "startTime": "$startTime",
                "endTime": "$endTime"
            }
        """.trimIndent()

}
