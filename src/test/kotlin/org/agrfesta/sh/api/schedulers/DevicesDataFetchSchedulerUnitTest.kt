package org.agrfesta.sh.api.schedulers

import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.SmartCache
import org.junit.jupiter.api.Test

class DevicesDataFetchSchedulerUnitTest {
    private val cache: Cache = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val switchBotDevicesClient: SwitchBotDevicesClient = mockk()

    private val mapper = ObjectMapper()
    private val smartCache = SmartCache(cache, mapper)
    private val switchBotService = SwitchBotService(devicesClient = switchBotDevicesClient, mapper = mapper)
    private val sut = DevicesDataFetchScheduler(
        devicesDao = devicesDao,
        switchBotService = switchBotService,
        cache = smartCache
    )

    @Test fun `fetchDevicesData() do not cache any value when there are no devices`() {
        every { devicesDao.getAll() } returns emptyList<Device>().right()

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

    @Test fun `fetchDevicesData() do not cache any value when there are no sensors`() {
        val noSensorDevice = aDevice(features = setOf(DeviceFeature.ACTUATOR))
        every { devicesDao.getAll() } returns listOf(noSensorDevice).right()

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

}
