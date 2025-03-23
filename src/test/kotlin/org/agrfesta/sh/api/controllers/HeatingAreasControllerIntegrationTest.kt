package org.agrfesta.sh.api.controllers

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.TemperatureInterval
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class HeatingAreasControllerIntegrationTest(
    @Autowired private val areasDao: AreaDao,
    @Autowired private val tempSettingsDao: TemperatureSettingsDao
): AbstractIntegrationTest() {

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()

        @Container
        @ServiceConnection
        val redis = createRedisContainer()
    }

    ///// createHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `createHeatingSchedule() return 404 when area is not found`() {
        val area = anArea()

        val result = given()
            .contentType(ContentType.JSON)
            .body("""{"defaultTemperature": ${aRandomTemperature()}, "temperatureSchedule": []}""")
            .`when`()
            .post("/heating/areas/${area.uuid}")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Area with id '${area.uuid}' is missing!"
    }

    @Test fun `createHeatingSchedule() return 201 when successfully creates area's heating schedule`() {
        val defaultTemperature = aRandomTemperature()
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val tempIntA = aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 1))
        val tempIntB = aTemperatureInterval(startTime = aDailyTime(hour = 2), endTime = aDailyTime(hour = 3))
        val tempIntC = aTemperatureInterval(startTime = aDailyTime(hour = 4), endTime = aDailyTime(hour = 5))
        val body = """
            {
                "defaultTemperature": $defaultTemperature,
                "temperatureSchedule": [
                    ${tempIntA.toJson()},
                    ${tempIntB.toJson()},
                    ${tempIntC.toJson()}
                ]
            }
        """.trimIndent()

        val result = given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/heating/areas/${area.uuid}")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Successfully created heating schedule for area with id '${area.uuid}'!"
        val savedSetting = tempSettingsDao.findAreaSetting(area.uuid).shouldBeRight().shouldNotBeNull()
        savedSetting.defaultTemperature shouldBe defaultTemperature
        savedSetting.temperatureSchedule.shouldContainExactlyInAnyOrder(tempIntB, tempIntC, tempIntA)
    }

    @Test fun `createHeatingSchedule() return 400 when an interval overlaps with another`() {
        val defaultTemperature = aRandomTemperature()
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val tempIntA = aTemperatureInterval(startTime = aDailyTime(hour = 4), endTime = aDailyTime(hour = 7))
        val tempIntB = aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6))
        val body = """
            {
                "defaultTemperature": $defaultTemperature,
                "temperatureSchedule": [
                    ${tempIntA.toJson()},
                    ${tempIntB.toJson()}
                ]
            }
        """.trimIndent()

        val result = given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/heating/areas/${area.uuid}")
            .then()
            .statusCode(400)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "A couple of intervals overlaps, this is not allowed!"
    }

    @Test fun `createHeatingSchedule() return 201 when replaces existing area's heating schedule`() {
        val defaultTemperature = aRandomTemperature()
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val existingSetting = anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(aTemperatureInterval())
        )
        tempSettingsDao.createSetting(existingSetting)
        val tempIntA = aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 1))
        val tempIntB = aTemperatureInterval(startTime = aDailyTime(hour = 2), endTime = aDailyTime(hour = 3))
        val tempIntC = aTemperatureInterval(startTime = aDailyTime(hour = 4), endTime = aDailyTime(hour = 5))
        val body = """
            {
                "defaultTemperature": $defaultTemperature,
                "temperatureSchedule": [
                    ${tempIntA.toJson()},
                    ${tempIntB.toJson()},
                    ${tempIntC.toJson()}
                ]
            }
        """.trimIndent()

        val result = given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/heating/areas/${area.uuid}")
            .then()
            .statusCode(201)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Successfully created heating schedule for area with id '${area.uuid}'!"
        val savedSetting = tempSettingsDao.findAreaSetting(area.uuid).shouldBeRight().shouldNotBeNull()
        savedSetting.defaultTemperature shouldBe defaultTemperature
        savedSetting.temperatureSchedule.shouldContainExactlyInAnyOrder(tempIntB, tempIntC, tempIntA)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// deleteHeatingSchedule ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `deleteHeatingSchedule() returns 200 when successfully deletes area's heating schedule`() {
        val area = anArea()
        areasDao.save(area).shouldBeRight()
        val existingSetting = anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(aTemperatureInterval())
        )
        tempSettingsDao.createSetting(existingSetting)
        tempSettingsDao.findAreaSetting(area.uuid).shouldBeRight().shouldNotBeNull()

        val result = given()
            .`when`()
            .delete("/heating/areas/${area.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Successfully deleted heating schedule for area with id '${area.uuid}'!"
        tempSettingsDao.findAreaSetting(area.uuid).shouldBeRight().shouldBeNull()
    }

    @Test fun `deleteHeatingSchedule() returns 404 when area is not found`() {
        val area = anArea()

        val result = given()
            .`when`()
            .delete("/heating/areas/${area.uuid}")
            .then()
            .statusCode(404)
            .extract()
            .`as`(MessageResponse::class.java)

        result.message shouldBe "Area with id '${area.uuid}' is missing!"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun TemperatureInterval.toJson() = """
            {
                "temperature": $temperature,
                "startTime": "$startTime",
                "endTime": "$endTime"
            }
        """.trimIndent()

}
