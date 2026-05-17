package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.util.*

class AreasJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: AreasJdbcAdapter

    // getAreaById()

    @Test
    fun `getAreaById() Returns AreaNotFound when area is missing`() {
        every { timeProvider.now() } returns Instant.now()
        val missingAreaId = UUID.randomUUID()

        sut.getAreaById(missingAreaId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `getAreaById() Returns area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(
            name = aRandomUniqueString(),
            isIndoor = true
        ).also { areasRepo.persist(it) }

        sut.getAreaById(area.uuid)
            .shouldBeRight().also {
                it.name shouldBe area.name
                it.isIndoor.shouldBeTrue()
            }
    }

    @Test
    fun `getAreaById() Returns AreaRepositoryError when fails to fetch area`() {
        every { timeProvider.now() } returns Instant.now()
        val areaId = UUID.randomUUID()
        val failure = DataAccessResourceFailureException("are fetching failure")
        every { areasRepo.findAreaById(areaId) } throws failure

        sut.getAreaById(areaId)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // findAreaByName()

    @Test
    fun `findAreaByName() Returns area when found`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }

        sut.findAreaByName(area.name)
            .shouldBeRight().also {
                it?.name shouldBe area.name
            }
    }

    @Test
    fun `findAreaByName() Returns null when area is not found`() {
        every { timeProvider.now() } returns Instant.now()

        sut.findAreaByName(aRandomUniqueString())
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findAreaByName() Returns AreaRepositoryError when fails to fetch area`() {
        every { timeProvider.now() } returns Instant.now()
        val name = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("area by name fetching failure")
        every { areasRepo.findAreaByName(name) } throws failure

        sut.findAreaByName(name)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // save()

    @Test
    fun `save() Persists area successfully`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString())

        sut.save(area).shouldBeRight()

        sut.getAreaById(area.uuid)
            .shouldBeRight().also {
                it.name shouldBe area.name
            }
    }

    @Test
    fun `save() Returns AreaNameConflict when area with same name already exists`() {
        every { timeProvider.now() } returns Instant.now()
        val name = aRandomUniqueString()
        areasRepo.persist(anAreaDto(name = name))

        sut.save(anAreaDto(name = name))
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNameConflict>()
    }

    @Test
    fun `save() Returns AreaRepositoryError when fails to persist area`() {
        every { timeProvider.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("area creation failure")
        every { areasRepo.persist(any()) } throws failure

        sut.save(anAreaDto())
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // getAll()

    @Test
    fun `getAll() Returns empty collection when no areas exist`() {
        every { timeProvider.now() } returns Instant.now()

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(0)
    }

    @Test
    fun `getAll() Returns all persisted areas`() {
        every { timeProvider.now() } returns Instant.now()
        areasRepo.persist(anAreaDto(name = aRandomUniqueString()))
        areasRepo.persist(anAreaDto(name = aRandomUniqueString()))

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getAll() Returns AreaRepositoryError when fails to fetch areas`() {
        every { timeProvider.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("areas fetching failure")
        every { areasRepo.getAll() } throws failure

        sut.getAll()
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // update()

    @Test
    fun `update() Returns AreaRepositoryError when repository throws DataAccessException`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString())
        val failure = DataAccessResourceFailureException("update failure")
        every { areasRepo.update(area) } throws failure

        sut.update(area)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    @Test
    fun `update() Returns AreaNameConflict when new name clashes`() {
        every { timeProvider.now() } returns Instant.now()
        val existingName = aRandomUniqueString()
        areasRepo.persist(anAreaDto(name = existingName))
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }

        sut.update(area.copy(name = existingName))
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNameConflict>()
    }

    @Test
    fun `update() Returns AreaNotFound when area does not exist`() {
        every { timeProvider.now() } returns Instant.now()
        val missingAreaId = UUID.randomUUID()

        sut.update(anAreaDto(uuid = missingAreaId, name = aRandomUniqueString()))
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `update() Updates area successfully`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
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
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }

        sut.deleteAreaById(area.uuid).shouldBeRight()

        sut.getAreaById(area.uuid)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
    }

    @Test
    fun `deleteAreaById() Returns AreaNotFound when area does not exist`() {
        every { timeProvider.now() } returns Instant.now()
        val missingAreaId = UUID.randomUUID()

        sut.deleteAreaById(missingAreaId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `deleteAreaById() Returns AreaRepositoryError when fails to delete area`() {
        every { timeProvider.now() } returns Instant.now()
        val areaId = UUID.randomUUID()
        val failure = DataAccessResourceFailureException("area deletion failure")
        every { areasRepo.deleteAreaById(areaId) } throws failure

        sut.deleteAreaById(areaId)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }
}
