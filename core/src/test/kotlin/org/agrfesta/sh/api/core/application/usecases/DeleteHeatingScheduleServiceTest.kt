package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.anAreaDto
import org.junit.jupiter.api.Test

class DeleteHeatingScheduleServiceTest {

    private val areasRepository: AreasRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()

    private val sut = DeleteHeatingScheduleService(areasRepository, temperatureSettingsRepository)

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
    fun `execute() returns AreaRepositoryError when area fetch fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaRepositoryError.left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)

        verify(exactly = 0) { temperatureSettingsRepository.deleteAreaSetting(any()) }
    }

    @Test
    fun `execute() returns PersistenceFailure when deleteAreaSetting fails`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.deleteAreaSetting(areaId) } returns PersistenceFailure(Exception()).left()

        sut.execute(areaId)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `execute() returns Unit when area exists and deletion succeeds`() {
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsRepository.deleteAreaSetting(areaId) } returns Unit.right()

        sut.execute(areaId).shouldBeRight()
    }

}
