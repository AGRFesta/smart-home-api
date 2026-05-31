package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.*

class ReplaceHeatingScheduleServiceTest {

    private val areasRepository: AreasRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)

    private val sut = ReplaceHeatingScheduleService(
        areasRepository, temperatureSettingsRepository, homeStateRefreshPublisher
    )

    @Test
    fun `execute() Returns OverlappingIntervals without calling repositories when intervals overlap`() {
        val overlapping = listOf(
            TemperatureInterval(aRandomTemperature(), LocalTime.of(8, 0), LocalTime.of(10, 0)),
            TemperatureInterval(aRandomTemperature(), LocalTime.of(9, 0), LocalTime.of(11, 0))
        )

        sut.execute(UUID.randomUUID(), aRandomTemperature(), overlapping)
            .shouldBeLeft()
            .shouldBe(OverlappingIntervals)

        verify(exactly = 0) { areasRepository.getAreaById(any()) }
        verify(exactly = 0) { temperatureSettingsRepository.createSetting(any()) }
    }

    @Test
    fun `execute() Returns AreaNotFound when area does not exist`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.execute(areaId, aRandomTemperature(), emptyList())
            .shouldBeLeft()
            .shouldBe(AreaNotFound(areaId))

        verify(exactly = 0) { temperatureSettingsRepository.createSetting(any()) }
    }

    @Test
    fun `execute() Returns HeatingScheduleRepositoryError when createSetting fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.createSetting(any()) } returns HeatingScheduleRepositoryError.left()

        sut.execute(areaId, aRandomTemperature(), emptyList())
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }

    @Test
    fun `execute() Returns HeatingScheduleDto with correct data on success`() {
        val areaId = UUID.randomUUID()
        val defaultTemperature = aRandomTemperature()
        val interval1 = TemperatureInterval(aRandomTemperature(), LocalTime.of(8, 0), LocalTime.of(10, 0))
        val interval2 = TemperatureInterval(aRandomTemperature(), LocalTime.of(12, 0), LocalTime.of(14, 0))

        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.createSetting(any()) } returns Unit.right()

        val result = sut.execute(areaId, defaultTemperature, listOf(interval1, interval2)).shouldBeRight()

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
    fun `execute() publishes home state refresh after a successful save`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.createSetting(any()) } returns Unit.right()

        sut.execute(areaId, aRandomTemperature(), emptyList())

        verify { homeStateRefreshPublisher.publish() }
    }

    @Test
    fun `execute() does not publish home state refresh when the save fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.createSetting(any()) } returns HeatingScheduleRepositoryError.left()

        sut.execute(areaId, aRandomTemperature(), emptyList())

        verify(exactly = 0) { homeStateRefreshPublisher.publish() }
    }

    @Test
    fun `execute() Returns HeatingScheduleDto with empty intervals when no intervals provided`() {
        val areaId = UUID.randomUUID()
        val defaultTemperature = aRandomTemperature()

        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.createSetting(any()) } returns Unit.right()

        val result = sut.execute(areaId, defaultTemperature, emptyList()).shouldBeRight()

        result.defaultTemperature shouldBe defaultTemperature
        result.intervals shouldHaveSize 0
    }
}
