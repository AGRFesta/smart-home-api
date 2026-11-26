package org.agrfesta.sh.api.core.application.usecases.areas

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.utils.FakeAreasRepository
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.util.*

class UpdateAreaServiceTest {
    private val areasRepository = FakeAreasRepository()

    private val sut = UpdateAreaService(areasRepository)

    @Test
    fun `execute() Returns updated Area on success`() {
        // Given
        val existing = anArea()
        areasRepository.givenArea(existing)
        val updatedArea = existing.copy(name = aRandomUniqueString(), isIndoor = !existing.isIndoor)

        // When
        val result = sut.execute(existing.uuid, updatedArea.name, updatedArea.isIndoor)

        // Then
        result.shouldBeRight() shouldBe updatedArea
        withClue("stored area should carry the new name and isIndoor") {
            areasRepository.storedArea(existing.uuid) shouldBe updatedArea
        }
    }

    @Test
    fun `execute() Returns AreaNameConflict when new name clashes`() {
        // Given
        val other = anArea()
        val existing = anArea()
        areasRepository.givenArea(other, existing)

        // When
        val result = sut.execute(existing.uuid, other.name, true)

        // Then
        result.shouldBeLeft() shouldBe AreaNameConflict
    }

    @Test
    fun `execute() Returns AreaNotFound when area does not exist`() {
        // Given
        val areaId = UUID.randomUUID()

        // When
        val result = sut.execute(areaId, aRandomUniqueString(), true)

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<AreaNotFound>()
        failure.missingAreaId shouldBe areaId
    }

    @Test
    fun `execute() Returns AreaRepositoryError when repository fails`() {
        // Given
        val existing = anArea()
        areasRepository.givenArea(existing)
        areasRepository.givenWriteFails()

        // When
        val result = sut.execute(existing.uuid, aRandomUniqueString(), true)

        // Then
        result.shouldBeLeft().shouldBe(AreaRepositoryError)
    }
}
