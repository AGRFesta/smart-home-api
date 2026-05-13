package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.util.*
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test

class CreateAreaServiceTest {
    private val areasRepository: AreasRepository = mockk()
    private val randomGenerator: RandomGenerator = mockk()

    private val sut = CreateAreaService(areasRepository, randomGenerator)

    @Test
    fun `execute() Returns created area on success`() {
        val uuid = UUID.randomUUID()
        val name = aRandomUniqueString()
        every { randomGenerator.uuid() } returns uuid
        every { areasRepository.save(any()) } returns Unit.right()

        val result = sut.execute(name).shouldBeRight()

        result.uuid shouldBe uuid
        result.name shouldBe name
        result.isIndoor shouldBe true
    }

    @Test
    fun `execute() Creates indoor area by default`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { areasRepository.save(any()) } returns Unit.right()

        sut.execute(aRandomUniqueString()).shouldBeRight().isIndoor shouldBe true
    }

    @Test
    fun `execute() Creates outdoor area when isIndoor is false`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { areasRepository.save(any()) } returns Unit.right()

        sut.execute(aRandomUniqueString(), isIndoor = false).shouldBeRight().isIndoor shouldBe false
    }

    @Test
    fun `execute() Returns AreaNameConflict when area name already exists`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { areasRepository.save(any()) } returns AreaNameConflict.left()

        sut.execute(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBe(AreaNameConflict)
    }

    @Test
    fun `execute() Returns PersistenceFailure when repository fails`() {
        every { randomGenerator.uuid() } returns UUID.randomUUID()
        every { areasRepository.save(any()) } returns PersistenceFailure(Exception("db error")).left()

        sut.execute(aRandomUniqueString())
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

}
