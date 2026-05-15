package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned
import org.agrfesta.test.mothers.aProvider
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class SensorsAssignmentsJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: SensorsAssignmentsJdbcAdapter

    @Test
    fun `assign() Returns AssignmentRepositoryError when area is missing`() {
        every { timeProvider.now() } returns Instant.now()
        val device = aProviderDeviceData(
            providerId = aRandomUniqueString(),
            provider = aProvider(),
            name = aRandomUniqueString(),
            features = emptySet() // TODO various features
        )
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, device)
        val missingAreaId = UUID.randomUUID()

        sut.assign(missingAreaId, deviceId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `assign() Returns AssignmentRepositoryError when device is missing`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(
            name = aRandomUniqueString(),
            isIndoor = true
        ).also { areasRepo.persist(it) }
        val missingDeviceId = UUID.randomUUID()

        sut.assign(area.uuid, missingDeviceId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `assign() Returns SameAreaAssignment when sensor is already assigned to that area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorProviderData())
        sensorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = sensorId)

        sut.assign(area.uuid, sensorId)
            .shouldBeLeft()
            .shouldBe(SameAreaAssignment)
    }

    @Test
    fun `assign() Returns SensorAlreadyAssigned when sensor is already assigned to another area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val anotherArea = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorProviderData())
        sensorsAssignmentsRepo.persistAssignment(areaId = anotherArea.uuid, deviceId = sensorId)

        sut.assign(area.uuid, sensorId)
            .shouldBeLeft()
            .shouldBe(SensorAlreadyAssigned)
    }

    @Test
    fun `assign() Returns AssignmentRepositoryError when findByDevice throws a DataAccessException`() {
        val areaId = UUID.randomUUID()
        val sensorId = UUID.randomUUID()
        every { sensorsAssignmentsRepo.findByDevice(sensorId) } throws object : DataAccessException("DB failure") {}

        sut.assign(areaId, sensorId)
            .shouldBeLeft()
            .shouldBe(AssignmentRepositoryError)
    }

    @Test
    fun `assign() Assigns sensor to area`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        every { timeProvider.now() } returns now
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorProviderData())

        sut.assign(area.uuid, sensorId).shouldBeRight()

        sensorsAssignmentsRepo.findByDevice(sensorId).shouldNotBeEmpty()
            .first().also {
                it.areaUuid shouldBe area.uuid
                it.sensorUuid shouldBe sensorId
                it.connectedOn shouldBe now
                it.disconnectedOn.shouldBeNull()
            }
    }

}
