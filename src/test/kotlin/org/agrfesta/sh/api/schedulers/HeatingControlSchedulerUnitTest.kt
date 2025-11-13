package org.agrfesta.sh.api.schedulers

import arrow.core.right
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anAreaDtoWithDevices
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SharedHeater
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class HeatingControlSchedulerUnitTest {
    private val sensor: Sensor = mockk()
    private val heater: SharedHeater = mockk()
    private val notHeatableArea = anAreaDtoWithDevices()
    private val devicesService: DevicesService = mockk()
    private val areasService: AreasService = mockk()

    private val sut = HeatingControlScheduler(devicesService, areasService)

    @Test
    fun `scheduledTask() Do nothing when there are no shared heaters`() {
        every { devicesService.getAllDevices() } returns listOf(sensor).right()

        sut.scheduledTask()

        //TODO don't know how verify it

    }

    @Test
    fun `scheduledTask() Do nothing when there are no heatable areas`() {
        val area = anAreaDtoWithDevices(
            devices = emptyList() // an area with no devices can't be heatable
        )
        every { devicesService.getAllDevices() } returns listOf(sensor, heater).right()
        every { areasService.getAllAreasWithDevices() } returns listOf(area)

        sut.scheduledTask()

        coVerify(exactly = 0) { heater.on() }
        coVerify(exactly = 0) { heater.off() }
    }

    @Test
    fun `scheduledTask() Turn the heater on when the greatest difference from target temperature is negative`() {
        val sensorADto = aSensor()
        val sensorA: Sensor = mockk()
        every { sensorA.uuid } returns sensorADto.uuid
        val sensorBDto = aSensor()
        val sensorB: Sensor = mockk()
        every { sensorB.uuid } returns sensorBDto.uuid
        val areaA = anAreaDtoWithDevices(
            devices = listOf(sensorADto)
        )
        val areaB = anAreaDtoWithDevices(
            devices = listOf(sensorBDto)
        )
        every { devicesService.getAllDevices() } returns listOf(sensorA, sensorB, heater).right()
        every { areasService.getAllAreasWithDevices() } returns listOf(areaB, areaA)

        sut.scheduledTask()

        coVerify(exactly = 1) { heater.on() }
        coVerify(exactly = 0) { heater.off() }
    }

}
