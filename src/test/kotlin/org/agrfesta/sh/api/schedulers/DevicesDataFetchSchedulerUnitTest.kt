package org.agrfesta.sh.api.schedulers

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.devices.SwitchBotDevicesFactory
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.SmartCache
import org.junit.jupiter.api.Test

class DevicesDataFetchSchedulerUnitTest {
    private val cache: Cache = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val switchBotDevicesClient: SwitchBotDevicesClient = mockk()

    private val mapper = ObjectMapper()
    private val smartCache = SmartCache(cache, mapper)
    private val switchBotDevicesFactory = SwitchBotDevicesFactory(switchBotDevicesClient)
    private val devicesFactories: Collection<ProviderDevicesFactory> = listOf(switchBotDevicesFactory)
    private val sut = DevicesDataFetchScheduler(
        devicesService = DevicesService(devicesDao, devicesFactories),
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
        val failingSensor = aDevice(provider = Provider.SWITCHBOT, features = setOf(DeviceFeature.SENSOR))
        //val sensor = aDevice(provider = Provider.SWITCHBOT, features = setOf(DeviceFeature.SENSOR))
        every { devicesDao.getAll() } returns listOf(failingSensor)
        coEvery {
            switchBotDevicesClient.getDeviceStatus(failingSensor.deviceProviderId)
        } throws failure

        sut.fetchDevicesData()

        coVerify { switchBotDevicesClient.getDeviceStatus(any()) }
        verify(exactly = 0) { cache.set(any(), any()) }
    }

}
