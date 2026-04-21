package org.agrfesta.sh.api.persistence.jdbc.dao

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.aSensorDataValue
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned
import org.agrfesta.test.mothers.aProvider
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class SensorsAssignmentsDaoJdbcImplTest : AbstractDaoJdbcImplTest() {

    @Autowired private lateinit var sut: SensorsAssignmentsJdbcAdapter

    @Test
    fun `assign() Returns AreaNotFound when area is missing`() {
        every { timeService.now() } returns Instant.now()
        val device = aDeviceDataValue(
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
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `assign() Returns DeviceNotFound when area is missing`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(
            name = aRandomUniqueString(),
            isIndoor = true
        ).also { areasRepo.persist(it) }
        val missingDeviceId = UUID.randomUUID()

        sut.assign(area.uuid, missingDeviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe missingDeviceId
    }

    @Test
    fun `assign() Returns SameAreaAssignment when sensor is already assigned to that area`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorDataValue())
        sensorsAssignmentsRepo.persistAssignment(areaId = area.uuid, deviceId = sensorId)

        sut.assign(area.uuid, sensorId)
            .shouldBeLeft()
            .shouldBe(SameAreaAssignment)
    }

    @Test
    fun `assign() Returns SensorAlreadyAssigned when sensor is already assigned to another area`() {
        every { timeService.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val anotherArea = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorDataValue())
        sensorsAssignmentsRepo.persistAssignment(areaId = anotherArea.uuid, deviceId = sensorId)

        sut.assign(area.uuid, sensorId)
            .shouldBeLeft()
            .shouldBe(SensorAlreadyAssigned)
    }

    @Test
    fun `assign() Returns PersistenceFailure when findByDevice throws a DataAccessException`() {
        val areaId = UUID.randomUUID()
        val sensorId = UUID.randomUUID()
        every { sensorsAssignmentsRepo.findByDevice(sensorId) } throws object : DataAccessException("DB failure") {}

        sut.assign(areaId, sensorId)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `assign() Assigns sensor to area`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        every { timeService.now() } returns now
        val area = anAreaDto(name = aRandomUniqueString(), isIndoor = true).also { areasRepo.persist(it) }
        val sensorId = UUID.randomUUID()
        devicesRepo.persist(sensorId, aSensorDataValue())

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
