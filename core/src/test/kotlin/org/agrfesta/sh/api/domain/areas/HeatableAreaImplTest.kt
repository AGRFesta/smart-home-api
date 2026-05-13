package org.agrfesta.sh.api.domain.areas

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.core.domain.areas.HeatableAreaImpl
import org.agrfesta.sh.api.core.domain.areas.MonitoredClimateArea
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.Heater
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

    @Test
    fun `getCurrentTargetTemperature() return null when there is no setting`() {
        val sut = HeatableAreaImpl(heater, mcArea, null)

        val result = sut.getCurrentTargetTemperature(LocalTime.now())

        result.shouldBeNull()
    }

    @TestFactory
    fun `getCurrentTargetTemperature() return correct target temperature based on current time`(): List<DynamicTest> {
        val settings = anAreaTemperatureSetting(
            areaId = areaId,
            defaultTemperature = Temperature.of("17.0"),
            temperatureSchedule = setOf(
                aTemperatureInterval(
                    Temperature.of("19.0"),
                    LocalTime.of(6, 0),
                    LocalTime.of(9, 0)
                ),
                aTemperatureInterval(
                    Temperature.of("20.0"),
                    LocalTime.of(21, 0),
                    LocalTime.of(23, 0)
                )
            )
        )
        val sut = HeatableAreaImpl(heater, mcArea, settings)
        return listOf(
            LocalTime.of(1, 30) to Temperature.of("17.0"), // Default
            LocalTime.of(6, 0) to Temperature.of("19.0"),  // Start of first interval
            LocalTime.of(7, 30) to Temperature.of("19.0"), // Inside first interval
            LocalTime.of(9, 0) to Temperature.of("17.0"),  // End of first interval (exclusive)
            LocalTime.of(15, 0) to Temperature.of("17.0"), // Default between intervals
            LocalTime.of(21, 0) to Temperature.of("20.0"), // Start of second interval
            LocalTime.of(22, 0) to Temperature.of("20.0"), // Inside second interval
            LocalTime.of(23, 0) to Temperature.of("17.0")  // End of second interval (exclusive)
        ).map { (time, expectedTemp) ->
            dynamicTest("at $time target should be $expectedTemp") {
                val result = sut.getCurrentTargetTemperature(time)

                result.shouldNotBeNull().shouldBe(expectedTemp)
            }
        }
    }
}
