package org.agrfesta.sh.api.schedulers

import arrow.core.left
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.FailureByException
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.SmartCache
import org.junit.jupiter.api.Test

class DevicesDataFetchSchedulerUnitTest {
    private val cache: Cache = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val sensorsAssignmentsDao: SensorsAssignmentsDao = mockk()

    private val mapper = ObjectMapper()
    private val smartCache = SmartCache(cache, mapper)
    private val switchBotService: SwitchBotService = mockk()
    private val sut = DevicesDataFetchScheduler(
        devicesService = DevicesService(devicesDao, sensorsAssignmentsDao),
        switchBotService = switchBotService,
        cache = smartCache
    )

    @Test fun `fetchDevicesData() do not cache any value when there are no devices`() {
        every { devicesDao.getAll() } returns emptyList()

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

    @Test fun `fetchDevicesData() do not cache any value when there are no sensors`() {
        val noSensorDevice = aDevice(features = setOf(DeviceFeature.ACTUATOR))
        every { devicesDao.getAll() } returns listOf(noSensorDevice)

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

    @Test fun `fetchDevicesData() do not cache any value when fails fetching sensor data`() {
        val failure = Exception()
        val sensor = aDevice(features = setOf(DeviceFeature.SENSOR))
        every { devicesDao.getAll() } returns listOf(sensor)
        coEvery { switchBotService.fetchSensorReadings(sensor.providerId) } returns FailureByException(failure).left()

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

}
