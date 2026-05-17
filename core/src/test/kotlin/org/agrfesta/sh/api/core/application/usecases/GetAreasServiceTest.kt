package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anAreaDto
import org.junit.jupiter.api.Test

class GetAreasServiceTest {
    private val areasRepository: AreasRepository = mockk()

    private val sut = GetAreasService(areasRepository)

    @Test
    fun `execute() Returns empty collection when no areas exist`() {
        // Given
        every { areasRepository.getAll() } returns emptyList<AreaDto>().right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight().shouldBeEmpty()
    }

    @Test
    fun `execute() Returns all areas when repository has data`() {
        // Given
        val area1 = anAreaDto()
        val area2 = anAreaDto()
        every { areasRepository.getAll() } returns listOf(area1, area2).right()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight().shouldContainExactlyInAnyOrder(area1, area2)
    }

    @Test
    fun `execute() Returns AreaRepositoryError when repository fails`() {
        // Given
        every { areasRepository.getAll() } returns AreaRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeLeft().shouldBe(AreaRepositoryError)
    }
}
