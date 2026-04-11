package org.agrfesta.sh.api.persistence.jdbc.dao

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.anActuatorDataValue
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class ActuatorsAssignmentsDaoJdbcImplTest : AbstractDaoJdbcImplTest() {

    @Autowired private lateinit var sut: ActuatorsAssignmentsDaoJdbcImpl

    @Test
    fun `assign() Returns AreaNotFound when area is missing`() {
        every { timeService.now() } returns Instant.now()
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorDataValue())
        val missingAreaId = UUID.randomUUID()

        sut.assign(missingAreaId, actuatorId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `assign() Returns DeviceNotFound when device is missing`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val missingActuatorId = UUID.randomUUID()

        sut.assign(area.uuid, missingActuatorId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe missingActuatorId
    }

    @Test
    fun `assign() Returns SameAreaAssignment when actuator is already assigned to that area`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorDataValue())
        actuatorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = actuatorId)

        sut.assign(area.uuid, actuatorId)
            .shouldBeLeft()
            .shouldBe(SameAreaAssignment)
    }

    @Test
    fun `assign() Returns PersistenceFailure when findByDevice throws a DataAccessException`() {
        val areaId = UUID.randomUUID()
        val actuatorId = UUID.randomUUID()
        every { actuatorsAssignmentsRepo.findByDevice(actuatorId) } throws object : DataAccessException("DB failure") {}

        sut.assign(areaId, actuatorId)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `assign() Assigns actuator to area`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorDataValue())

        sut.assign(area.uuid, actuatorId).shouldBeRight()

        actuatorsAssignmentsRepo.findByDevice(actuatorId).shouldNotBeEmpty()
            .first().also {
                it.areaUuid shouldBe area.uuid
                it.actuatorUuid shouldBe actuatorId
            }
    }

}
