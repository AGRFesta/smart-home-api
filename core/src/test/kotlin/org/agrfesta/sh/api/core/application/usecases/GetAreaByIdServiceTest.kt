package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anAreaDto
import org.junit.jupiter.api.Test
import java.util.UUID

class GetAreaByIdServiceTest {
    private val areasRepository: AreasRepository = mockk()

    private val sut = GetAreaByIdService(areasRepository)

    @Test
    fun `execute() Returns the area when found`() {
        // Given
        val area = anAreaDto()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()

        // When
        val result = sut.execute(area.uuid)

        // Then
        result.shouldBeRight() shouldBe area
    }

    @Test
    fun `execute() Returns AreaNotFound when area does not exist`() {
        // Given
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        // When
        val result = sut.execute(areaId)

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<AreaNotFound>()
        failure.missingAreaId shouldBe areaId
    }

    @Test
    fun `execute() Returns AreaRepositoryError when repository fails`() {
        // Given
        val areaId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaRepositoryError.left()

        // When
        val result = sut.execute(areaId)

        // Then
        result.shouldBeLeft().shouldBe(AreaRepositoryError)
    }

}
