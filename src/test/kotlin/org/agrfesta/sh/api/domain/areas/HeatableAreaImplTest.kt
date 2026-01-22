package org.agrfesta.sh.api.domain.areas

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.commons.temperatureOf
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.utils.TimeService
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class HeatableAreaImplTest {
    private val areaId = UUID.randomUUID()

    private val heater: Heater = mockk()
    private val mcArea: MonitoredClimateArea = mockk {
        every { uuid } returns areaId
    }
    private val heatingAreasService: HeatingAreasService = mockk()
    private val timeService: TimeService = mockk()

    private val sut = HeatableAreaImpl(heater, mcArea, heatingAreasService, timeService)

    @Test
    fun `getCurrentTargetTemperature() return null when there is no setting`() {
        every { heatingAreasService.findAreaSetting(areaId) } returns null.right()

        val result = sut.getCurrentTargetTemperature()

        result.shouldBeNull()
    }

    @Test
    fun `getCurrentTargetTemperature() return null when fails to fetch setting`() {
        val failure = Exception("fetch failure")
        every { heatingAreasService.findAreaSetting(areaId) } returns PersistenceFailure(failure).left()

        val result = sut.getCurrentTargetTemperature()

        result.shouldBeNull()
    }

    @TestFactory
    fun `getCurrentTargetTemperature() return correct target temperature based on current time`(): List<DynamicTest> {
        val settings = anAreaTemperatureSetting(
            areaId = areaId,
            defaultTemperature = temperatureOf("17.0"),
            temperatureSchedule = setOf(
                aTemperatureInterval(
                    temperatureOf("19.0"),
                    LocalTime.of(6, 0),
                    LocalTime.of(9, 0)
                ),
                aTemperatureInterval(
                    temperatureOf("20.0"),
                    LocalTime.of(21, 0),
                    LocalTime.of(23, 0)
                )
            )
        )
        every { heatingAreasService.findAreaSetting(areaId) } returns settings.right()
        return listOf(
            LocalTime.of(1, 30) to temperatureOf("17.0"), // Default
            LocalTime.of(6, 0) to temperatureOf("19.0"),  // Start of first interval
            LocalTime.of(7, 30) to temperatureOf("19.0"), // Inside first interval
            LocalTime.of(9, 0) to temperatureOf("17.0"),  // End of first interval (exclusive)
            LocalTime.of(15, 0) to temperatureOf("17.0"), // Default between intervals
            LocalTime.of(21, 0) to temperatureOf("20.0"), // Start of second interval
            LocalTime.of(22, 0) to temperatureOf("20.0"), // Inside second interval
            LocalTime.of(23, 0) to temperatureOf("17.0")  // End of second interval (exclusive)
        ).map { (time, expectedTemp) ->
            dynamicTest("at $time target should be $expectedTemp") {
                every { timeService.currentLocalTime() } returns time

                val result = sut.getCurrentTargetTemperature()

                result.shouldNotBeNull().shouldBe(expectedTemp)
            }
        }
    }
}
