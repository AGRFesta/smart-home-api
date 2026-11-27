package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anArea
import org.junit.jupiter.api.Test

/**
 * The infrastructure failures of [FakeAreasRepository], which [AreasRepositoryContract] leaves out.
 */
class FakeAreasRepositoryTest {
    private val sut = FakeAreasRepository()

    // givenReadFails()

    @Test
    fun `getAreaById() Returns AreaRepositoryError when reads fail`() {
        val area = anArea().also { sut.givenArea(it) }
        sut.givenReadFails()

        sut.getAreaById(area.uuid).shouldBeLeft() shouldBe AreaRepositoryError
    }

    @Test
    fun `findAreaByName() Returns AreaRepositoryError when reads fail`() {
        val area = anArea().also { sut.givenArea(it) }
        sut.givenReadFails()

        sut.findAreaByName(area.name).shouldBeLeft() shouldBe AreaRepositoryError
    }

    @Test
    fun `getAll() Returns AreaRepositoryError when reads fail`() {
        sut.givenArea(anArea())
        sut.givenReadFails()

        sut.getAll().shouldBeLeft() shouldBe AreaRepositoryError
    }

    // givenWriteFails()

    @Test
    fun `save() Returns AreaRepositoryError and stores nothing when writes fail`() {
        val area = anArea()
        sut.givenWriteFails()

        sut.save(area).shouldBeLeft() shouldBe AreaRepositoryError

        withClue("a failed save should store no area") {
            sut.storedArea(area.uuid) shouldBe null
        }
    }

    @Test
    fun `update() Returns AreaRepositoryError and keeps the stored area when writes fail`() {
        val area = anArea(isIndoor = true).also { sut.givenArea(it) }
        sut.givenWriteFails()

        sut.update(area.copy(isIndoor = false)).shouldBeLeft() shouldBe AreaRepositoryError

        withClue("a failed update should leave the stored area unchanged") {
            sut.storedArea(area.uuid) shouldBe area
        }
    }

    @Test
    fun `deleteAreaById() Returns AreaRepositoryError and keeps the stored area when writes fail`() {
        val area = anArea().also { sut.givenArea(it) }
        sut.givenWriteFails()

        sut.deleteAreaById(area.uuid).shouldBeLeft() shouldBe AreaRepositoryError

        withClue("a failed delete should leave the stored area in place") {
            sut.storedAreas().shouldContainExactly(area)
        }
    }
}
