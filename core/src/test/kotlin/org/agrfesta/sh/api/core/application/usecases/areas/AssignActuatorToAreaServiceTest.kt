package org.agrfesta.sh.api.core.application.usecases.areas

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.devices.DeviceModelCatalog
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.NotAnActuator
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aDevicePrototype
import org.agrfesta.sh.api.domain.anArea
import org.junit.jupiter.api.Test
import java.util.UUID

class AssignActuatorToAreaServiceTest {
    private val areasRepository: AreasRepository = mockk()
    private val devicesRepository: DevicesRepository = mockk()
    private val actuatorsAssignmentsRepository: ActuatorsAssignmentsRepository = mockk()

    private val actuatorModel = DeviceModel("test/actuator")
    private val sensorModel = DeviceModel("test/sensor")
    private val catalog = DeviceModelCatalog(
        listOf(
            aDevicePrototype(model = actuatorModel, roles = setOf(ACTUATOR)),
            aDevicePrototype(model = sensorModel, roles = setOf(SENSOR))
        )
    )

    private val sut = AssignActuatorToAreaService(
        areasRepository,
        devicesRepository,
        actuatorsAssignmentsRepository,
        catalog
    )

    @Test fun `execute() returns Right(Unit) when the device model has the ACTUATOR role`() {
        val area = anArea()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()
        val device = aDevice(model = actuatorModel)
        every { devicesRepository.getDeviceById(device.uuid) } returns device.right()
        every { actuatorsAssignmentsRepository.assign(area.uuid, device.uuid) } returns Unit.right()

        sut.execute(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeRight()
    }

    @Test fun `execute() returns NotAnActuator when the device model lacks the ACTUATOR role`() {
        val area = anArea()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()
        val device = aDevice(model = sensorModel)
        every { devicesRepository.getDeviceById(device.uuid) } returns device.right()

        sut.execute(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBeInstanceOf<NotAnActuator>().also {
                it.deviceId shouldBe device.uuid
                it.roles shouldBe setOf(SENSOR)
            }
    }

    @Test fun `execute() returns NotAnActuator with empty roles when the device model is unknown`() {
        val area = anArea()
        every { areasRepository.getAreaById(area.uuid) } returns area.right()
        val device = aDevice(model = DeviceModel("test/unknown"))
        every { devicesRepository.getDeviceById(device.uuid) } returns device.right()

        sut.execute(areaId = area.uuid, deviceId = device.uuid)
            .shouldBeLeft()
            .shouldBeInstanceOf<NotAnActuator>().also {
                it.deviceId shouldBe device.uuid
                it.roles shouldBe emptySet()
            }
    }

    @Test fun `execute() returns DeviceNotFound when device does not exist`() {
        val area = anArea()
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
