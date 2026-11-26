package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.junit.jupiter.api.Test

/**
 * The infrastructure failures of [FakePropertyRepository].
 */
class FakePropertyRepositoryTest {
    private val sut = FakePropertyRepository()

    // givenReadFails()

    @Test
    fun `findEntry() Returns PropertyRepositoryError when reads fail`() {
        sut.givenProperty("a.key", "a value")
        sut.givenReadFails()

        sut.findEntry("a.key").shouldBeLeft() shouldBe PropertyRepositoryError
    }

    @Test
    fun `getEntry() Returns PropertyRepositoryError when reads fail`() {
        sut.givenProperty("a.key", "a value")
        sut.givenReadFails()

        sut.getEntry("a.key").shouldBeLeft() shouldBe PropertyRepositoryError
    }
}
