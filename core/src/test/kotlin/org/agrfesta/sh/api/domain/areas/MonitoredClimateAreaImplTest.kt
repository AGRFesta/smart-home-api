package org.agrfesta.sh.api.domain.areas

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.util.*
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.areas.MonitoredClimateAreaImpl
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.failures.MessageFailure
import org.agrfesta.test.mothers.aThermoHygroDataValue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class MonitoredClimateAreaImplTest {

    @Test
    fun `creation raise IllegalArgumentException when area has no sensors`() {
        val uuid = UUID.randomUUID()
        val area: Area = mockk()
        every { area.sensors } returns emptyList()
        every { area.uuid } returns uuid

        shouldThrow<IllegalArgumentException> { MonitoredClimateAreaImpl(area) }
            .shouldHaveMessage("MonitoredClimateAreaImpl must have at least one sensor! Area '$uuid'")
    }

    /**
     * Data class representing a single test scenario for temperature averaging.
     */
    private data class AverageTestCase(
        val description: String,
        val temperatures: List<String>,
        val expectedAverage: String
    )

    @TestFactory
    fun `getCurrentTemperature() returns average temperature among all sensors`(): List<DynamicTest> {
        val testCases = listOf(
            // Simple integer average: (10+11+12)/3 = 11
            AverageTestCase("Exact integer average", listOf("10", "11", "12"), "11"),

            // Decimal average within scale: (10.5+11.5)/2 = 11.0 -> stripped to 11
            AverageTestCase("Stripped trailing zeros", listOf("10.5", "11.5"), "11"),

            // Rounding Case: (10.123 + 10.456) / 2 = 10.2895 -> Rounds to 10.29
            AverageTestCase("Round HALF_UP to 2 decimals", listOf("10.123", "10.456"), "10.29"),

            // Rounding Case: (10.121 + 10.452) / 2 = 10.2865 -> Rounds to 10.29
            AverageTestCase("Rounding half up verification", listOf("10.121", "10.452"), "10.29"),

            // Half-way case: (10.10 + 10.11) / 2 = 10.105 -> Rounds to 10.11
            AverageTestCase("Mid-point rounding", listOf("10.10", "10.11"), "10.11")
        )

        return testCases.map { case ->
            DynamicTest.dynamicTest(case.description) {
                val mockedSensors = case.temperatures.map { temp ->
                    mockk<Sensor> {
                        every { fetchReadings() } returns aThermoHygroDataValue(
                            temperature = Temperature.of(temp)
                        ).right()
                    }
                }
                val area = mockk<Area> {
                    every { sensors } returns mockedSensors
                }
                val sut = MonitoredClimateAreaImpl(area)

                val result = sut.getCurrentTemperature()

                result.shouldBeRight().value.toPlainString() shouldBe case.expectedAverage
            }
        }
    }

    @Test
    fun `getCurrentTemperature() returns average temperature among all sensors ignoring failing fetches`() {
        val sensorSuccess1 = mockk<Sensor> {
            every { fetchReadings() } returns aThermoHygroDataValue(temperature = Temperature.of("10")).right()
        }
        val sensorSuccess2 = mockk<Sensor> {
            every { fetchReadings() } returns aThermoHygroDataValue(temperature = Temperature.of("20")).right()
        }
        val sensorFailure = mockk<Sensor> {
            every { uuid } returns UUID.randomUUID()
            every { fetchReadings() } returns MessageFailure("Connection Timeout").left()
        }
        val area = mockk<Area> {
            every { sensors } returns listOf(sensorSuccess1, sensorFailure, sensorSuccess2)
        }
        val sut = MonitoredClimateAreaImpl(area)

        val result = sut.getCurrentTemperature()

        result.shouldBeRight().value.toPlainString() shouldBe "15"
    }

    @Test
    fun `getCurrentTemperature() fails when all sensors fail`() {
        val areaId = UUID.randomUUID()
        val failureResponse = MessageFailure("Sensor Unreachable").left()
        val sensorA = mockk<Sensor> {
            every { uuid } returns UUID.randomUUID()
            every { fetchReadings() } returns failureResponse
        }
        val sensorB = mockk<Sensor> {
            every { uuid } returns UUID.randomUUID()
            every { fetchReadings() } returns failureResponse
        }
        val area = mockk<Area> {
            every { uuid } returns areaId
            every { sensors } returns listOf(sensorA, sensorB)
        }

        val sut = MonitoredClimateAreaImpl(area)

        val result = sut.getCurrentTemperature()
        result.shouldBeLeft().let {
            it.shouldBeInstanceOf<MessageFailure>()
            it.message shouldBe "It was not possible to retrieve the current temperature for area '$areaId'"
        }
    }

}
