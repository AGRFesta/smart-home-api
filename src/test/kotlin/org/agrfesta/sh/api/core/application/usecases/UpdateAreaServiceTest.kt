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
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.util.UUID

class UpdateAreaServiceTest {
    private val areasRepository: AreasRepository = mockk()

    private val sut = UpdateAreaService(areasRepository)

    @Test
    fun `execute() Returns updated AreaDto on success`() {
        // Given
        val existing = anAreaDto()
        val updatedArea = existing.copy(name = aRandomUniqueString(), isIndoor = !existing.isIndoor)
        every { areasRepository.update(updatedArea) } returns updatedArea.right()

        // When
        val result = sut.execute(existing.uuid, updatedArea.name, updatedArea.isIndoor)

        // Then
        result.shouldBeRight() shouldBe updatedArea
    }

    @Test
    fun `execute() Returns AreaNameConflict when new name clashes`() {
        // Given
        every { areasRepository.update(any()) } returns AreaNameConflict.left()

        // When
        val result = sut.execute(UUID.randomUUID(), aRandomUniqueString(), true)

        // Then
        result.shouldBeLeft() shouldBe AreaNameConflict
    }

    @Test
    fun `execute() Returns AreaNotFound when area does not exist`() {
        // Given
        val areaId = UUID.randomUUID()
        every { areasRepository.update(any()) } returns AreaNotFound(areaId).left()

        // When
        val result = sut.execute(areaId, aRandomUniqueString(), true)

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<AreaNotFound>()
        failure.missingAreaId shouldBe areaId
    }

    @Test
    fun `execute() Returns PersistenceFailure when repository fails`() {
        // Given
        every { areasRepository.update(any()) } returns PersistenceFailure(RuntimeException("db error")).left()

        // When
        val result = sut.execute(UUID.randomUUID(), aRandomUniqueString(), true)

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

}
