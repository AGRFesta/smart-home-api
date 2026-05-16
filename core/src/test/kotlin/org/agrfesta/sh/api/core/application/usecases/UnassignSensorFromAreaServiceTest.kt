package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.SensorNotAssigned
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.aSensor
import org.junit.jupiter.api.Test

class UnassignSensorFromAreaServiceTest {
    private val areasRepository: AreasRepository = mockk()
    private val devicesRepository: DevicesRepository = mockk()
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository = mockk()

    private val sut = UnassignSensorFromAreaService(areasRepository, devicesRepository, sensorsAssignmentsRepository)

    @Test fun `execute() returns Right(Unit) on success`() {
        val area = anAreaDto()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()
        val device = aSensor()
        every { devicesRepository.getDeviceById(device.uuid) } returns device.right()
        every { sensorsAssignmentsRepository.unassign(area.uuid, device.uuid) } returns Unit.right()

        sut.execute(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeRight()
    }

    @Test fun `execute() returns SensorNotAssigned when no active assignment found for this area`() {
        val area = anAreaDto()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()
        val device = aSensor()
        every { devicesRepository.getDeviceById(device.uuid) } returns device.right()
        every { sensorsAssignmentsRepository.unassign(area.uuid, device.uuid) } returns SensorNotAssigned.left()

        sut.execute(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBe(SensorNotAssigned)
    }

    @Test fun `execute() returns DeviceNotFound when device does not exist`() {
        val area = anAreaDto()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()
        val deviceId = UUID.randomUUID()
        every { devicesRepository.getDeviceById(deviceId) } returns DeviceNotFound(deviceId).left()

        sut.execute(areaId = area.uuid, deviceId = deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe deviceId
    }

    @Test fun `execute() returns AreaNotFound when area does not exist`() {
        val areaId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        every { areasRepository.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.execute(areaId = areaId, deviceId = deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe areaId
    }

}
