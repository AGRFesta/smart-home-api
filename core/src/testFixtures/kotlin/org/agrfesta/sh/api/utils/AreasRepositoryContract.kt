package org.agrfesta.sh.api.utils

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Domain outcomes of [AreasRepository], run on every implementation of the port.
 * Every test starts from an empty repository.
 * `docs/TDD_WORKFLOW.md` § Contract tests of a fake owns the rules of a contract test.
 */
abstract class AreasRepositoryContract {

    protected abstract val sut: AreasRepository

    // getAreaById()

    @Test
    fun `getAreaById() Returns AreaNotFound when area is missing`() {
        val missingAreaId = UUID.randomUUID()

        sut.getAreaById(missingAreaId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `getAreaById() Returns area`() {
        val area = anArea(
            name = aRandomUniqueString(),
            isIndoor = true
        ).also { sut.save(it).shouldBeRight() }

        sut.getAreaById(area.uuid)
            .shouldBeRight().also {
                it.name shouldBe area.name
                it.isIndoor.shouldBeTrue()
            }
    }

    // findAreaByName()

    @Test
    fun `findAreaByName() Returns area when found`() {
        val area = anArea(name = aRandomUniqueString()).also { sut.save(it).shouldBeRight() }

        sut.findAreaByName(area.name)
            .shouldBeRight().also {
                it?.name shouldBe area.name
            }
    }

    @Test
    fun `findAreaByName() Returns null when area is not found`() {
        sut.findAreaByName(aRandomUniqueString())
            .shouldBeRight()
            .shouldBeNull()
    }

    // save()

    @Test
    fun `save() Persists area successfully`() {
        val area = anArea(name = aRandomUniqueString())

        sut.save(area).shouldBeRight()

        sut.getAreaById(area.uuid)
            .shouldBeRight().also {
                it.name shouldBe area.name
            }
    }

    @Test
    fun `save() Persists area with isIndoor false`() {
        val area = anArea(name = aRandomUniqueString(), isIndoor = false)

        sut.save(area).shouldBeRight()

        withClue("persisted area should keep isIndoor = false") {
            sut.getAreaById(area.uuid)
                .shouldBeRight()
                .isIndoor shouldBe false
        }
    }

    @Test
    fun `save() Persists area with isIndoor true`() {
        val area = anArea(name = aRandomUniqueString(), isIndoor = true)

        sut.save(area).shouldBeRight()

        withClue("persisted area should keep isIndoor = true") {
            sut.getAreaById(area.uuid)
                .shouldBeRight()
                .isIndoor shouldBe true
        }
    }

    @Test
    fun `save() Returns AreaNameConflict when area with same name already exists`() {
        val name = aRandomUniqueString()
        sut.save(anArea(name = name)).shouldBeRight()

        sut.save(anArea(name = name))
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNameConflict>()
    }

    // getAll()

    @Test
    fun `getAll() Returns empty collection when no areas exist`() {
        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(0)
    }

    @Test
    fun `getAll() Returns all persisted areas`() {
        sut.save(anArea(name = aRandomUniqueString())).shouldBeRight()
        sut.save(anArea(name = aRandomUniqueString())).shouldBeRight()

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    // update()

    @Test
    fun `update() Returns AreaNameConflict when new name clashes`() {
        val existingName = aRandomUniqueString()
        sut.save(anArea(name = existingName)).shouldBeRight()
        val area = anArea(name = aRandomUniqueString()).also { sut.save(it).shouldBeRight() }

        sut.update(area.copy(name = existingName))
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNameConflict>()
    }

    @Test
    fun `update() Returns AreaNotFound when area does not exist`() {
        val missingAreaId = UUID.randomUUID()

        sut.update(anArea(uuid = missingAreaId, name = aRandomUniqueString()))
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `update() Updates area successfully`() {
        val area = anArea(name = aRandomUniqueString(), isIndoor = true).also { sut.save(it).shouldBeRight() }
        val updated = area.copy(name = aRandomUniqueString(), isIndoor = false)

        val result = sut.update(updated)

        result.shouldBeRight().also {
            it.name shouldBe updated.name
            it.isIndoor shouldBe false
        }
        sut.getAreaById(area.uuid).shouldBeRight().also {
            it.name shouldBe updated.name
            it.isIndoor shouldBe false
        }
    }

    // deleteAreaById()

    @Test
    fun `deleteAreaById() Deletes area successfully`() {
        val area = anArea(name = aRandomUniqueString()).also { sut.save(it).shouldBeRight() }

        sut.deleteAreaById(area.uuid).shouldBeRight()

        sut.getAreaById(area.uuid)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
    }

    @Test
    fun `deleteAreaById() Returns AreaNotFound when area does not exist`() {
        val missingAreaId = UUID.randomUUID()

        sut.deleteAreaById(missingAreaId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }
}
