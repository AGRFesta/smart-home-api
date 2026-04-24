package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test

class GetHeatingScheduleServiceTest {

    private val areasRepository: AreasRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()

    private val sut = GetHeatingScheduleService(areasRepository, temperatureSettingsRepository)

    @Test
    fun `execute() returns AreaNotFound when area does not exist`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBe(AreaNotFound(areaId))

        verify(exactly = 0) { temperatureSettingsRepository.findAreaSetting(any()) }
    }

    @Test
    fun `execute() returns PersistenceFailure when findAreaSetting fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.findAreaSetting(areaId) } returns PersistenceFailure(Exception()).left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `execute() returns null when area exists but has no schedule`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.findAreaSetting(areaId) } returns null.right()

        sut.execute(areaId).shouldBeRight().shouldBe(null)
    }

    @Test
    fun `execute() returns HeatingScheduleDto with correct data when schedule exists`() {
        val areaId = UUID.randomUUID()
        val defaultTemperature = aRandomTemperature()
        val interval1 = TemperatureInterval(aRandomTemperature(), LocalTime.of(8, 0), LocalTime.of(10, 0))
        val interval2 = TemperatureInterval(aRandomTemperature(), LocalTime.of(12, 0), LocalTime.of(14, 0))

        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.findAreaSetting(areaId) } returns AreaTemperatureSetting(
            areaId = areaId,
            defaultTemperature = defaultTemperature,
            temperatureSchedule = linkedSetOf(interval1, interval2)
        ).right()

        val result = sut.execute(areaId).shouldBeRight()!!

        result.defaultTemperature shouldBe defaultTemperature
        result.intervals shouldHaveSize 2

        val dto1 = result.intervals[0]
        dto1.temperature shouldBe interval1.temperature
        dto1.startTime shouldBe interval1.startTime
        dto1.endTime shouldBe interval1.endTime

        val dto2 = result.intervals[1]
        dto2.temperature shouldBe interval2.temperature
        dto2.startTime shouldBe interval2.startTime
        dto2.endTime shouldBe interval2.endTime
    }

    @Test
    fun `execute() returns HeatingScheduleDto with empty intervals when schedule has no intervals`() {
        val areaId = UUID.randomUUID()
        val defaultTemperature = aRandomTemperature()

        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.findAreaSetting(areaId) } returns AreaTemperatureSetting(
            areaId = areaId,
            defaultTemperature = defaultTemperature,
            temperatureSchedule = emptySet()
        ).right()

        val result = sut.execute(areaId).shouldBeRight()!!

        result.defaultTemperature shouldBe defaultTemperature
        result.intervals shouldHaveSize 0
    }

}
