package org.agrfesta.sh.api.persistence.jdbc.dao

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import java.time.Instant
import java.util.UUID
import org.agrfesta.sh.api.domain.anActuatorDataValue
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException

class AreasWithDevicesDaoJdbcImplTest : AbstractDaoJdbcImplTest() {

    @Autowired private lateinit var sut: AreasWithDevicesRepositoryJdbcImpl

    // getAllAreasWithDevices()

    @Test
    fun `getAllAreasWithDevices() Returns empty collection when no areas exist`() {
        every { timeService.now() } returns Instant.now()

        sut.getAllAreasWithDevices()
            .shouldBeRight()
            .shouldHaveSize(0)
    }

    @Test
    fun `getAllAreasWithDevices() Returns all areas without devices`() {
        every { timeService.now() } returns Instant.now()
        areasRepo.persist(anAreaDto(name = aRandomUniqueString()))
        areasRepo.persist(anAreaDto(name = aRandomUniqueString()))

        sut.getAllAreasWithDevices()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getAllAreasWithDevices() Returns area with assigned sensor`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorDataValue())
        sensorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = sensorId)

        val result = sut.getAllAreasWithDevices().shouldBeRight()

        result.shouldHaveSize(1)
        result.first().also {
            it.uuid shouldBe area.uuid
            it.sensors.shouldHaveSize(1)
            it.actuators.shouldHaveSize(0)
            it.sensors.first().uuid shouldBe sensorId
        }
    }

    @Test
    fun `getAllAreasWithDevices() Returns area with assigned actuator`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val actuatorId = UUID.randomUUID()
        devicesRepo.persist(actuatorId, anActuatorDataValue())
        actuatorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = actuatorId)

        val result = sut.getAllAreasWithDevices().shouldBeRight()

        result.shouldHaveSize(1)
        result.first().also {
            it.uuid shouldBe area.uuid
            it.sensors.shouldHaveSize(0)
            it.actuators.shouldHaveSize(1)
            it.actuators.first().uuid shouldBe actuatorId
        }
    }

    @Test
    fun `getAllAreasWithDevices() Returns PersistenceFailure when fails to fetch`() {
        every { timeService.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("areas with devices fetching failure")
        every { areasWithDevicesRepo.getAll() } throws failure

        sut.getAllAreasWithDevices()
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

}
