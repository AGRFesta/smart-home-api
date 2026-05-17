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
import org.junit.jupiter.api.Test
import java.util.UUID

class DeleteAreaServiceTest {
    private val areasRepository: AreasRepository = mockk()

    private val sut = DeleteAreaService(areasRepository)

    @Test
    fun `execute() Returns Unit on success`() {
        // Given
        val areaId = UUID.randomUUID()
        every { areasRepository.deleteAreaById(areaId) } returns Unit.right()

        // When
        val result = sut.execute(areaId)

        // Then
        result.shouldBeRight()
    }

    @Test
    fun `execute() Returns AreaNotFound when area does not exist`() {
        // Given
        val areaId = UUID.randomUUID()
        every { areasRepository.deleteAreaById(areaId) } returns AreaNotFound(areaId).left()

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
        every { areasRepository.deleteAreaById(areaId) } returns AreaRepositoryError.left()

        // When
        val result = sut.execute(areaId)

        // Then
        result.shouldBeLeft().shouldBe(AreaRepositoryError)
    }
}
