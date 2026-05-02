package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.cache.adapters.getThermoHygroKey
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.CachedValueNotFound
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.springframework.stereotype.Service

@Service
class CacheIntegrationAsserter(
    private val cache: Cache
) {

    fun verifyContainsThermoHygroDataFrom(sensor: ProviderDeviceData, data: ThermoHygroData) {
        cache.get(sensor.getThermoHygroKey()).shouldBeRight() shouldEqualJson """{
            "relativeHumidity":${data.relativeHumidity.value},
            "temperature":${data.temperature.value}}
        """.trimIndent()
    }
    fun verifyContainsNoThermoHygroDataFrom(sensor: ProviderDeviceData) {
        cache.get(sensor.getThermoHygroKey())
            .shouldBeLeft() shouldBe CachedValueNotFound(sensor.getThermoHygroKey())
    }

}
