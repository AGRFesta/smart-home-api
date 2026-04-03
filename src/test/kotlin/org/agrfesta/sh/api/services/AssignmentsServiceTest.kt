package org.agrfesta.sh.api.services

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.util.*
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.NotAnActuator
import org.agrfesta.sh.api.domain.failures.NotASensor
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.junit.jupiter.api.Test

class AssignmentsServiceTest {
    private val areasDao: AreasDao = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val sensorsAssignmentsDao: SensorsAssignmentsDao = mockk()
    private val actuatorsAssignmentsDao: ActuatorsAssignmentsDao = mockk()

    private val sut = AssignmentsService(areasDao, devicesDao, sensorsAssignmentsDao, actuatorsAssignmentsDao)

    @Test
    fun `assignSensorToArea() Returns AreaNotFound when area is missing`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.assignSensorToArea(areaId = areaId, deviceId = deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe areaId
    }

    @Test
    fun `assignSensorToArea() Returns DeviceNotFound when device is missing`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val deviceId = UUID.randomUUID()
        every { devicesDao.getDeviceById(deviceId) } returns DeviceNotFound(deviceId).left()

        sut.assignSensorToArea(areaId = area.uuid, deviceId = deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe deviceId
    }

    @Test
    fun `assignSensorToArea() Returns NotASensor when device is not a sensor`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = anActuator()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()

        sut.assignSensorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBeInstanceOf<NotASensor>().also {
                it.deviceId shouldBe device.uuid
                it.features shouldBe device.features
            }
    }

    @Test
    fun `assignSensorToArea() Returns SameAreaAssignment when sensor is already assigned to that area`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = aSensor()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()
        every { sensorsAssignmentsDao.assign(area.uuid, device.uuid) } returns SameAreaAssignment.left()

        sut.assignSensorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBe(SameAreaAssignment)
    }

    @Test
    fun `assignSensorToArea() Returns SensorAlreadyAssigned when sensor is already assigned to another area`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = aSensor()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()
        every { sensorsAssignmentsDao.assign(area.uuid, device.uuid) } returns SensorAlreadyAssigned.left()

        sut.assignSensorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBe(SensorAlreadyAssigned)
    }

    @Test
    fun `assignSensorToArea() Assigns sensor to area`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = aSensor()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()
        every { sensorsAssignmentsDao.assign(area.uuid, device.uuid) } returns Unit.right()

        sut.assignSensorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeRight()
    }

    @Test
    fun `assignActuatorToArea() Returns AreaNotFound when area is missing`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.assignActuatorToArea(areaId = areaId, deviceId = deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe areaId
    }

    @Test
    fun `assignActuatorToArea() Returns DeviceNotFound when device is missing`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val deviceId = UUID.randomUUID()
        every { devicesDao.getDeviceById(deviceId) } returns DeviceNotFound(deviceId).left()

        sut.assignActuatorToArea(areaId = area.uuid, deviceId = deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe deviceId
    }

    @Test
    fun `assignActuatorToArea() Returns NotAnActuator when device is not an actuator`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = aSensor()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()

        sut.assignActuatorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBeInstanceOf<NotAnActuator>()
    }

    @Test
    fun `assignActuatorToArea() Returns SameAreaAssignment when actuator is already assigned to that area`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = anActuator()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()
        every { actuatorsAssignmentsDao.assign(area.uuid, device.uuid) } returns SameAreaAssignment.left()

        sut.assignActuatorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBe(SameAreaAssignment)
    }

    @Test
    fun `assignActuatorToArea() Assigns actuator to area`() {
        val area = anAreaDto()
        every { areasDao.getAreaById(area.uuid) } returns area.right()
        val device = anActuator()
        every { devicesDao.getDeviceById(device.uuid) } returns device.right()
        every { actuatorsAssignmentsDao.assign(area.uuid, device.uuid) } returns Unit.right()

        sut.assignActuatorToArea(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeRight()
    }

}
