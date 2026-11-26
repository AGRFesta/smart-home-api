package org.agrfesta.sh.api.core.application.usecases.areas

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.utils.FakeAreasRepository
import org.junit.jupiter.api.Test
import java.util.UUID

class GetAreaByIdServiceTest {
    private val areasRepository = FakeAreasRepository()

    private val sut = GetAreaByIdService(areasRepository)

    @Test
    fun `execute() Returns the area when found`() {
        // Given
        val area = anArea()
        areasRepository.givenArea(area, anArea())

        // When
        val result = sut.execute(area.uuid)

        // Then
        result.shouldBeRight() shouldBe area
    }

    @Test
    fun `execute() Returns AreaNotFound when area does not exist`() {
        // Given
        val areaId = UUID.randomUUID()

        // When
        val result = sut.execute(areaId)

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<AreaNotFound>()
        failure.missingAreaId shouldBe areaId
    }

    @Test
    fun `execute() Returns AreaRepositoryError when repository fails`() {
        // Given
        val area = anArea()
        areasRepository.givenArea(area)
        areasRepository.givenReadFails()

        // When
        val result = sut.execute(area.uuid)

        // Then
        result.shouldBeLeft().shouldBe(AreaRepositoryError)
    }
}
