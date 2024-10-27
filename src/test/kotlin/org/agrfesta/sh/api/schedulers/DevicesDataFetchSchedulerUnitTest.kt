package org.agrfesta.sh.api.schedulers

import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDeviceStatusResponse
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheOkResponse
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class DevicesDataFetchSchedulerUnitTest {
    private val cache: Cache = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val switchBotDevicesClient: SwitchBotDevicesClient = mockk()

    private val mapper = ObjectMapper()
    private val switchBotService = SwitchBotService(devicesClient = switchBotDevicesClient, mapper = mapper)
    private val sut = DevicesDataFetchScheduler(
        devicesDao = devicesDao,
        switchBotService = switchBotService,
        cache = cache
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

    @TestFactory
    fun correctlyCacheSwitchBotTemperatureTextValues() = listOf(
        "0", "33.3333", "-41", "100", "-15.22"
    ).map {
        dynamicTest(it) {
            val response = mapper.aSwitchBotDeviceStatusResponse(temperatureText = it)
            val sensor = aSensor()
            every { devicesDao.getAll() } returns listOf(sensor).right()
            coEvery { switchBotDevicesClient.getDeviceStatus(sensor.providerId) } returns response
            val tempSlot = slot<String>()
            every { cache.set("sensors:switchbot:${sensor.providerId}:temperature", capture(tempSlot)) } returns
                    CacheOkResponse
            every { cache.set("sensors:switchbot:${sensor.providerId}:humidity", any()) } returns CacheOkResponse

            sut.fetchDevicesData()

            tempSlot.captured shouldBe it
        }
    }

}
