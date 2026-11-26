package org.agrfesta.sh.api.core.application.usecases.areas

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.utils.FakeAreasRepository
import org.junit.jupiter.api.Test

class GetAreasServiceTest {
    private val areasRepository = FakeAreasRepository()

    private val sut = GetAreasService(areasRepository)

    @Test
    fun `execute() Returns empty collection when no areas exist`() {
        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight().shouldBeEmpty()
    }

    @Test
    fun `execute() Returns all areas when repository has data`() {
        // Given
        val area1 = anArea()
        val area2 = anArea()
        areasRepository.givenArea(area1, area2)

        // When
        val result = sut.execute()

        // Then
        result.shouldBeRight().shouldContainExactlyInAnyOrder(area1, area2)
    }

    @Test
    fun `execute() Returns AreaRepositoryError when repository fails`() {
        // Given
        areasRepository.givenArea(anArea())
        areasRepository.givenReadFails()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeLeft().shouldBe(AreaRepositoryError)
    }
}
