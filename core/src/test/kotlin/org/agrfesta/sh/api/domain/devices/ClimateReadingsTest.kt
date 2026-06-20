package org.agrfesta.sh.api.domain.devices

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.SensorReadings
import org.agrfesta.sh.api.core.domain.devices.averageTemperature
import org.agrfesta.test.mothers.aThermoHygroDataValue
import org.junit.jupiter.api.Test

class ClimateReadingsTest {

    @Test
    fun `averageTemperature() returns null when there are no readings`() {
        emptyList<SensorReadings>().averageTemperature().shouldBeNull()
    }

    @Test
    fun `averageTemperature() returns the average of the readings temperatures`() {
        val readings = listOf("10", "11", "12")
            .map { aThermoHygroDataValue(temperature = Temperature.of(it)) }

        readings.averageTemperature() shouldBe Temperature.of("11")
    }
}
