package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.junit.jupiter.api.Test

/**
 * The infrastructure failures of [FakePropertyRepository].
 */
class FakePropertyRepositoryTest {
    private val sut = FakePropertyRepository()

    // givenUpsertFails()

    @Test
    fun `upsert() Returns PropertyRepositoryError and stores nothing when upserts fail`() {
        sut.givenUpsertFails()

        sut.upsert("a.key", "a value").shouldBeLeft() shouldBe PropertyRepositoryError

        withClue("a failed upsert should store no value") {
            sut.storedValue("a.key").shouldBeNull()
        }
    }

    @Test
    fun `upsertBatch() Returns PropertyRepositoryError and stores nothing when upserts fail`() {
        sut.givenUpsertFails()

        sut.upsertBatch(listOf(PropertyUpsertEntry("a.key", "a value"), PropertyUpsertEntry("b.key", "b value")))
            .shouldBeLeft() shouldBe PropertyRepositoryError

        withClue("a failed batch upsert should store none of its values") {
            sut.storedValue("a.key").shouldBeNull()
            sut.storedValue("b.key").shouldBeNull()
        }
    }

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
