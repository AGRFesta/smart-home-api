package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.domain.anAreaDto
import org.junit.jupiter.api.Test
import java.util.UUID

class DeleteHeatingScheduleServiceTest {

    private val areasRepository: AreasRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)

    private val sut = DeleteHeatingScheduleService(
        areasRepository,
        temperatureSettingsRepository,
        homeStateRefreshPublisher
    )

    @Test
    fun `execute() returns AreaNotFound when area does not exist`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBe(AreaNotFound(areaId))

        verify(exactly = 0) { temperatureSettingsRepository.deleteAreaSetting(any()) }
    }

    @Test
    fun `execute() returns HeatingScheduleRepositoryError when area fetch fails with AreaRepositoryError`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaRepositoryError.left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)

        verify(exactly = 0) { temperatureSettingsRepository.deleteAreaSetting(any()) }
    }

    @Test
    fun `execute() returns HeatingScheduleRepositoryError when deleteAreaSetting fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.deleteAreaSetting(areaId) } returns HeatingScheduleRepositoryError.left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }

    @Test
    fun `execute() returns Unit when area exists and deletion succeeds`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.deleteAreaSetting(areaId) } returns Unit.right()

        sut.execute(areaId).shouldBeRight()
    }

    @Test
    fun `execute() publishes home state refresh after a successful deletion`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.deleteAreaSetting(areaId) } returns Unit.right()

        sut.execute(areaId)

        verify { homeStateRefreshPublisher.publish() }
    }

    @Test
    fun `execute() does not publish home state refresh when the deletion fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.deleteAreaSetting(areaId) } returns HeatingScheduleRepositoryError.left()

        sut.execute(areaId)

        verify(exactly = 0) { homeStateRefreshPublisher.publish() }
    }
}
