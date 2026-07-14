package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.agrfesta.sh.api.core.application.readmodels.devices.AssignmentRole
import org.agrfesta.sh.api.core.application.readmodels.devices.DeviceAreaAssignment
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus.PAIRED
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.domain.anActuatorProviderData
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DeviceViewJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: DeviceViewRepositoryJdbcImpl

    // findById()

    @Test
    fun `findById() returns DeviceNotFound when the device does not exist`() {
        // Given
        val deviceId = UUID.randomUUID()

        // When / Then
        sut.findById(deviceId)
            .shouldBeLeft()
            .shouldBe(DeviceNotFound(deviceId))
    }

    @Test
    fun `findById() returns the view with base fields and empty assignments for an unassigned device`() {
        // Given
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        every { timeProvider.now() } returns now
        val deviceId = UUID.randomUUID()
        val data = aProviderDeviceData()
        devicesRepo.persist(deviceId, data)

        // When
        val view = sut.findById(deviceId).shouldBeRight()

        // Then
        view.uuid shouldBe deviceId
        view.deviceProviderId shouldBe data.deviceProviderId
        view.provider shouldBe data.provider
        view.name shouldBe data.name
        view.model shouldBe data.model
        view.status shouldBe PAIRED
        view.createdOn shouldBe now
        view.updatedOn shouldBe null
        view.assignments.shouldBeEmpty()
    }

    @Test
    fun `findById() includes the current SENSOR assignment of the device`() {
        // Given
        every { timeProvider.now() } returns Instant.now()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, aSensorProviderData())
        val area = anArea(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        sensorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = deviceId)

        // When
        val view = sut.findById(deviceId).shouldBeRight()

        // Then
        withClue("expected the device's current sensor assignment to '${area.name}' with role SENSOR") {
            view.assignments.shouldContainExactly(
                DeviceAreaAssignment(areaUuid = area.uuid, areaName = area.name, role = AssignmentRole.SENSOR)
            )
        }
    }

    @Test
    fun `findById() includes the ACTUATOR assignment of the device`() {
        // Given
        every { timeProvider.now() } returns Instant.now()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, anActuatorProviderData())
        val area = anArea(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        actuatorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = deviceId)

        // When
        val view = sut.findById(deviceId).shouldBeRight()

        // Then
        withClue("expected the device's actuator assignment to '${area.name}' with role ACTUATOR") {
            view.assignments.shouldContainExactly(
                DeviceAreaAssignment(areaUuid = area.uuid, areaName = area.name, role = AssignmentRole.ACTUATOR)
            )
        }
    }

    @Test
    fun `findById() folds both the current SENSOR and ACTUATOR assignments of the same device`() {
        // Given
        every { timeProvider.now() } returns Instant.now()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, aProviderDeviceData())
        val sensorArea = anArea(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val actuatorArea = anArea(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        sensorsAssignmentsRepo.persistAssignment(areaId = sensorArea.uuid, deviceId = deviceId)
        actuatorsAssignmentsRepo.persistAssignment(areaId = actuatorArea.uuid, deviceId = deviceId)

        // When
        val view = sut.findById(deviceId).shouldBeRight()

        // Then
        withClue("expected both the current SENSOR and ACTUATOR assignments folded into one view") {
            view.assignments.shouldContainExactlyInAnyOrder(
                DeviceAreaAssignment(sensorArea.uuid, sensorArea.name, AssignmentRole.SENSOR),
                DeviceAreaAssignment(actuatorArea.uuid, actuatorArea.name, AssignmentRole.ACTUATOR)
            )
        }
    }

    @Test
    fun `findById() excludes sensor assignments that have been disconnected`() {
        // Given
        every { timeProvider.now() } returns Instant.now()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, aSensorProviderData())
        val area = anArea(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        sensorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = deviceId)
        sensorsAssignmentsRepo.disconnectSensor(areaId = area.uuid, deviceId = deviceId)

        // When
        val view = sut.findById(deviceId).shouldBeRight()

        // Then
        withClue("a disconnected sensor assignment is historical and must not be returned") {
            view.assignments.shouldBeEmpty()
        }
    }

    @Test
    fun `findById() returns DeviceRepositoryError when the query fails`() {
        // Given
        val deviceId = UUID.randomUUID()
        every { deviceViewRepo.findViewById(deviceId) } throws
            DataAccessResourceFailureException("device view fetching failure")

        // When / Then
        sut.findById(deviceId)
            .shouldBeLeft()
            .shouldBe(DeviceRepositoryError)
    }
}
