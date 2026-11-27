package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.utils.AreasRepositoryContract
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.util.*

class AreasJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var adapter: AreasJdbcAdapter

    @Nested
    inner class Contract : AreasRepositoryContract() {
        override val sut: AreasRepository get() = adapter

        @BeforeEach
        fun stubCreationAndUpdateInstant() {
            every { timeProvider.now() } returns Instant.now()
        }
    }

    // getAreaById()

    @Test
    fun `getAreaById() Returns AreaRepositoryError when fails to fetch area`() {
        every { timeProvider.now() } returns Instant.now()
        val areaId = UUID.randomUUID()
        val failure = DataAccessResourceFailureException("are fetching failure")
        every { areasRepo.findAreaById(areaId) } throws failure

        adapter.getAreaById(areaId)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // findAreaByName()

    @Test
    fun `findAreaByName() Returns AreaRepositoryError when fails to fetch area`() {
        every { timeProvider.now() } returns Instant.now()
        val name = aRandomUniqueString()
        val failure = DataAccessResourceFailureException("area by name fetching failure")
        every { areasRepo.findAreaByName(name) } throws failure

        adapter.findAreaByName(name)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // save()

    @Test
    fun `save() Returns AreaRepositoryError when fails to persist area`() {
        every { timeProvider.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("area creation failure")
        every { areasRepo.persist(any()) } throws failure

        adapter.save(anArea())
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // getAll()

    @Test
    fun `getAll() Returns AreaRepositoryError when fails to fetch areas`() {
        every { timeProvider.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("areas fetching failure")
        every { areasRepo.getAll() } throws failure

        adapter.getAll()
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // update()

    @Test
    fun `update() Returns AreaRepositoryError when repository throws DataAccessException`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anArea(name = aRandomUniqueString())
        val failure = DataAccessResourceFailureException("update failure")
        every { areasRepo.update(area) } throws failure

        adapter.update(area)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }

    // deleteAreaById()

    @Test
    fun `deleteAreaById() Returns AreaRepositoryError when fails to delete area`() {
        every { timeProvider.now() } returns Instant.now()
        val areaId = UUID.randomUUID()
        val failure = DataAccessResourceFailureException("area deletion failure")
        every { areasRepo.deleteAreaById(areaId) } throws failure

        adapter.deleteAreaById(areaId)
            .shouldBeLeft()
            .shouldBe(AreaRepositoryError)
    }
}
