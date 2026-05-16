package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.anActuatorProviderData
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.core.domain.failures.ActuatorNotAssigned
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class ActuatorsAssignmentsJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: ActuatorsAssignmentsJdbcAdapter

    @Test
    fun `assign() Returns AssignmentRepositoryError when area is missing`() {
        every { timeProvider.now() } returns Instant.now()
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorProviderData())
        val missingAreaId = UUID.randomUUID()

        sut.assign(missingAreaId, actuatorId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `assign() Returns AssignmentRepositoryError when device is missing`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val missingActuatorId = UUID.randomUUID()

        sut.assign(area.uuid, missingActuatorId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `assign() Returns SameAreaAssignment when actuator is already assigned to that area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorProviderData())
        actuatorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = actuatorId)

        sut.assign(area.uuid, actuatorId)
            .shouldBeLeft()
            .shouldBe(SameAreaAssignment)
    }

    @Test
    fun `assign() Returns AssignmentRepositoryError when findByDevice throws a DataAccessException`() {
        val areaId = UUID.randomUUID()
        val actuatorId = UUID.randomUUID()
        every { actuatorsAssignmentsRepo.findByDevice(actuatorId) } throws object : DataAccessException("DB failure") {}

        sut.assign(areaId, actuatorId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `unassign() Returns AssignmentRepositoryError when a DataAccessException is thrown`() {
        val areaId = UUID.randomUUID()
        val actuatorId = UUID.randomUUID()
        every { actuatorsAssignmentsRepo.findByDevice(actuatorId) } throws object : DataAccessException("DB failure") {}

        sut.unassign(areaId, actuatorId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `unassign() Returns Right(Unit) and the row is deleted on success`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorProviderData())
        actuatorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = actuatorId)

        sut.unassign(area.uuid, actuatorId).shouldBeRight()

        actuatorsAssignmentsRepo.findByDevice(actuatorId).shouldBeEmpty()
    }

    @Test
    fun `unassign() Returns ActuatorNotAssigned when no assignment row exists for this area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorProviderData())

        sut.unassign(area.uuid, actuatorId)
            .shouldBeLeft()
            .shouldBe(ActuatorNotAssigned)
    }

    @Test
    fun `assign() Assigns actuator to area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorProviderData())

        sut.assign(area.uuid, actuatorId).shouldBeRight()

        actuatorsAssignmentsRepo.findByDevice(actuatorId).shouldNotBeEmpty()
            .first().also {
                it.areaUuid shouldBe area.uuid
                it.actuatorUuid shouldBe actuatorId
            }
    }

}
