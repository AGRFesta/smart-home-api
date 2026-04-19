package org.agrfesta.sh.api.persistence.utils

import arrow.core.left
import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.time.Instant
import org.agrfesta.sh.api.CleanSmartHomeDatabase
import org.agrfesta.sh.api.TestContainersConfig
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    TestContainersConfig::class,
    AreasJdbcRepository::class,
    SpringUnitOfWork::class
)
@CleanSmartHomeDatabase
class SpringUnitOfWorkTest {
    @Autowired
    private lateinit var areasRepo: AreasJdbcRepository
    @Autowired
    private lateinit var sut: SpringUnitOfWork

    @MockkBean
    private lateinit var timeService: TimeService

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `execute() Commits transaction and returns Right when block succeeds`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString())

        val result = sut.execute {
            areasRepo.persist(area)
            area.right()
        }

        result.shouldBeRight() shouldBe area
        areasRepo.findAreaById(area.uuid).shouldNotBeNull()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `execute() Rolls back transaction and returns Left when block fails`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString())
        val failure = "persistence-failure"

        val result = sut.execute {
            areasRepo.persist(area)
            failure.left()
        }

        result.shouldBeLeft() shouldBe failure
        areasRepo.findAreaById(area.uuid).shouldBeNull()
    }
}
