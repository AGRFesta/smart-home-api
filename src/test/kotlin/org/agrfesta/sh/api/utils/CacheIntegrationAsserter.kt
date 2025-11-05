package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.springframework.stereotype.Service

@Service
class CacheIntegrationAsserter(
    private val cache: Cache
) {

    fun verifyContainsThermoHygroDataFrom(sensor: DeviceDataValue, data: ThermoHygroData) {
        cache.get(sensor.getThermoHygroKey()).shouldBeRight() shouldEqualJson """{
            "h":"${data.relativeHumidity.value}",
            "t":"${data.temperature}"}
        """.trimIndent()
    }
    fun verifyContainsNoThermoHygroDataFrom(sensor: DeviceDataValue) {
        cache.get(sensor.getThermoHygroKey())
            .shouldBeLeft() shouldBe CachedValueNotFound(sensor.getThermoHygroKey())
    }

}
