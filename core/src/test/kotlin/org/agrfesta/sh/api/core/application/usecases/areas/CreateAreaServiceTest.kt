package org.agrfesta.sh.api.core.application.usecases.areas

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.utils.FakeAreasRepository
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.util.*

class CreateAreaServiceTest {
    private val areasRepository = FakeAreasRepository()
    private val randomGenerator: RandomGenerator = mockk()

    private val sut = CreateAreaService(areasRepository, randomGenerator)

    @Test
    fun `execute() Returns created area on success`() {
        val uuid = UUID.randomUUID()
        val name = aRandomUniqueString()
        every { randomGenerator.uuid() } returns uuid

        val result = sut.execute(name).shouldBeRight()

        result.uuid shouldBe uuid
        result.name shouldBe name
        result.isIndoor shouldBe true
        withClue("created area should be stored as returned") {
            areasRepository.storedArea(uuid) shouldBe Area(uuid = uuid, name = name, isIndoor = true)
        }
    }

    @Test
    fun `execute() Creates indoor area by default`() {
        val uuid = UUID.randomUUID()
        every { randomGenerator.uuid() } returns uuid

        sut.execute(aRandomUniqueString()).shouldBeRight().isIndoor shouldBe true

        withClue("area created without isIndoor should be stored as indoor") {
            areasRepository.storedArea(uuid)?.isIndoor shouldBe true
        }
    }

    @Test
    fun `execute() Creates outdoor area when isIndoor is false`() {
        val uuid = UUID.randomUUID()
        every { randomGenerator.uuid() } returns uuid

        sut.execute(aRandomUniqueString(), isIndoor = false).shouldBeRight().isIndoor shouldBe false

        withClue("area created with isIndoor = false should be stored as outdoor") {
            areasRepository.storedArea(uuid)?.isIndoor shouldBe false
        }
    }

    @Test
    fun `execute() Returns AreaNameConflict when area name already exists`() {
        val name = aRandomUniqueString()
        areasRepository.givenArea(anArea(name = name))
        every { randomGenerator.uuid() } returns UUID.randomUUID()

        sut.execute(name)
            .shouldBeLeft()
            .shouldBe(AreaNameConflict)
    }

    @Test
    fun `execute() Returns AreaRepositoryError when repository fails`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        areasRepository.givenWriteFails()

        sut.execute(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }
}
