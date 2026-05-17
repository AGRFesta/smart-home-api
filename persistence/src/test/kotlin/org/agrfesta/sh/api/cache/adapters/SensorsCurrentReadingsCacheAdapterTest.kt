package org.agrfesta.sh.api.cache.adapters

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.cache.dto.toCacheDto
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupError
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SensorsCurrentReadingsCacheAdapterTest : AbstractCacheAdapterTest() {

    @Autowired private lateinit var sut: SensorsCurrentReadingsCacheAdapter
    @Autowired private lateinit var cache: Cache
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test fun `findBy() returns Right(null) when no data is cached for the sensor`() {
        val sensor = aSensor()

        sut.findBy(sensor).shouldBeRight().shouldBeNull()
    }

    @Test fun `findBy() returns the cached ThermoHygroData for the sensor`() {
        val sensor = aSensor()
        val data = aRandomThermoHygroData()
        cache.set(sensor.getThermoHygroKey(), objectMapper.writeValueAsString(data.toCacheDto()))

        sut.findBy(sensor).shouldBeRight() shouldBe data
    }

    @Test fun `findBy() returns Right(null) when another sensor has data but this one does not`() {
        val sensor = aSensor()
        val otherSensor = aSensor()
        val data = aRandomThermoHygroData()
        cache.set(otherSensor.getThermoHygroKey(), objectMapper.writeValueAsString(data.toCacheDto()))

        sut.findBy(sensor).shouldBeRight().shouldBeNull()
    }

    @Test fun `findBy() returns Left(ReadingsLookupError) when cached value is malformed JSON`() {
        val sensor = aSensor()
        cache.set(sensor.getThermoHygroKey(), "not-valid-json")

        sut.findBy(sensor)
            .shouldBeLeft()
            .shouldBeInstanceOf<ReadingsLookupError>()
    }

    @Test fun `save() stores serialized ThermoHygroDataCacheDto at the sensor's cache key`() {
        val sensor = aSensor()
        val data = aRandomThermoHygroData()

        sut.save(sensor, data).shouldBeRight()

        cache.get(sensor.getThermoHygroKey()).shouldBeRight() shouldBe
            objectMapper.writeValueAsString(data.toCacheDto())
    }

    @Test fun `save() overwrites previously stored data for the same sensor`() {
        val sensor = aSensor()
        val first = aRandomThermoHygroData()
        val second = aRandomThermoHygroData()
        sut.save(sensor, first)

        sut.save(sensor, second).shouldBeRight()

        cache.get(sensor.getThermoHygroKey()).shouldBeRight() shouldBe
            objectMapper.writeValueAsString(second.toCacheDto())
    }
}
