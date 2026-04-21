package org.agrfesta.sh.api.persistence.jdbc.dao

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.HUMIDITY
import org.agrfesta.sh.api.core.domain.devices.SensorDataType.TEMPERATURE
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.test.mothers.aRandomHumidity
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.nowNoMills
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class SensorsHistoryDataDaoJdbcImplTest : AbstractDaoJdbcImplTest() {

    @Autowired private lateinit var sut: SensorsHistoryDataJdbcAdapter

    // persistTemperature

    @Test
    fun `persistTemperature() Persists temperature reading for sensor`() {
        every { timeService.now() } returns Instant.now()
        val sensorUuid = UUID.randomUUID()
        devicesRepo.persist(sensorUuid, aSensorDataValue())
        val time = nowNoMills()
        val temperature = aRandomTemperature()

        sut.persistTemperature(sensorUuid, time, temperature).shouldBeRight()

        val saved = historyDataRepository.findAllBySensorUuid(sensorUuid)
        saved shouldHaveSize 1
        saved.first().apply {
            this.sensorUuid shouldBe sensorUuid
            this.type shouldBe TEMPERATURE
            this.value shouldBe temperature.value
            this.time shouldBe time
        }
    }

    @Test
    fun `persistTemperature() Returns PersistenceFailure on database error`() {
        every { historyDataRepository.persist(any(), any(), any(), any()) } throws
                object : DataAccessException("db error") {}

        sut.persistTemperature(UUID.randomUUID(), Instant.now(), aRandomTemperature())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // persistHumidity

    @Test
    fun `persistHumidity() Persists humidity reading for sensor`() {
        every { timeService.now() } returns Instant.now()
        val sensorUuid = UUID.randomUUID()
        devicesRepo.persist(sensorUuid, aSensorDataValue())
        val time = nowNoMills()
        val humidity = aRandomHumidity()

        sut.persistHumidity(sensorUuid, time, humidity).shouldBeRight()

        val saved = historyDataRepository.findAllBySensorUuid(sensorUuid)
        saved shouldHaveSize 1
        saved.first().apply {
            this.sensorUuid shouldBe sensorUuid
            this.type shouldBe HUMIDITY
            this.value shouldBe humidity.value
            this.time shouldBe time
        }
    }

    @Test
    fun `persistHumidity() Returns PersistenceFailure on database error`() {
        every { historyDataRepository.persist(any(), any(), any(), any()) } throws
                object : DataAccessException("db error") {}

        sut.persistHumidity(UUID.randomUUID(), Instant.now(), aRandomHumidity())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // findBySensor

    @Test
    fun `findBySensor() Returns empty collection when no history exists for sensor`() {
        every { timeService.now() } returns Instant.now()
        val sensorUuid = UUID.randomUUID()
        devicesRepo.persist(sensorUuid, aSensorDataValue())

        sut.findBySensor(sensorUuid)
            .shouldBeRight()
            .shouldBeEmpty()
    }

    @Test
    fun `findBySensor() Returns all history data for sensor`() {
        every { timeService.now() } returns Instant.now()
        val sensorUuid = UUID.randomUUID()
        devicesRepo.persist(sensorUuid, aSensorDataValue())
        val time = nowNoMills()
        sut.persistTemperature(sensorUuid, time, aRandomTemperature()).shouldBeRight()
        sut.persistHumidity(sensorUuid, time, aRandomHumidity()).shouldBeRight()

        sut.findBySensor(sensorUuid)
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `findBySensor() Returns PersistenceFailure on database error`() {
        every { historyDataRepository.findAllBySensorUuid(any()) } throws
                object : DataAccessException("db error") {}

        sut.findBySensor(UUID.randomUUID())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }
}
