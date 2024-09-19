package org.agrfesta.sh.api.schedulers

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.persistence.entities.aDeviceEntity
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDeviceStatusResponse
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheOkResponse
import org.agrfesta.test.mothers.aRandomHumidity
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test

class DevicesDataFetchSchedulerUnitTest {
    private val cache: Cache = mockk()
    private val devicesRepository: DevicesRepository = mockk()
    private val switchBotDevicesClient: SwitchBotDevicesClient = mockk()

    private val mapper = ObjectMapper()
    private val switchBotService = SwitchBotService(devicesClient = switchBotDevicesClient, mapper = mapper)
    private val sut = DevicesDataFetchScheduler(
        devicesRepository = devicesRepository,
        switchBotService = switchBotService,
        cache = cache
    )

    @Test
    fun `fetchDevicesData() do not cache any value when there are no devices`() {
        every { devicesRepository.findAll() } returns emptyList()

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

    @Test
    fun `fetchDevicesData() caches SwitchBot sensors device values only and ignores failures`() {
        val sensorTemperature = aRandomTemperature()
        val sensorHumidity = aRandomHumidity()
        val sensorAndMoreTemperature = aRandomTemperature()
        val sensorAndMoreHumidity = aRandomHumidity()
        val sensor = aDeviceEntity(features = setOf(SENSOR))
        val sensorAndMore = aDeviceEntity(features = setOf(SENSOR, ACTUATOR))
        val faultySensor = aDeviceEntity(features = setOf(SENSOR))
        val device = aDeviceEntity(features = emptySet())
        every { devicesRepository.findAll() } returns listOf(device, sensor, sensorAndMore, faultySensor)
        val tempSlot = slot<String>()
        every { cache.set("temp:switchbot:${sensor.providerId}", capture(tempSlot)) } returns CacheOkResponse
        val humSlot = slot<String>()
        every { cache.set("hum:switchbot:${sensor.providerId}", capture(humSlot)) } returns CacheOkResponse
        val tempAndMoreSlot = slot<String>()
        every { cache.set("temp:switchbot:${sensorAndMore.providerId}", capture(tempAndMoreSlot)) } returns CacheOkResponse
        val humAndMoreSlot = slot<String>()
        every { cache.set("hum:switchbot:${sensorAndMore.providerId}", capture(humAndMoreSlot)) } returns CacheOkResponse
        coEvery { switchBotDevicesClient.getDeviceStatus(sensor.providerId) } returns
                mapper.aSwitchBotDeviceStatusResponse(
                    humidity = sensorHumidity,
                    temperature = sensorTemperature
                )
        coEvery { switchBotDevicesClient.getDeviceStatus(sensorAndMore.providerId) } returns
                mapper.aSwitchBotDeviceStatusResponse(
                    humidity = sensorAndMoreHumidity,
                    temperature = sensorAndMoreTemperature
                )
        coEvery { switchBotDevicesClient.getDeviceStatus(faultySensor.providerId) } throws Exception("sensor readings failure")

        sut.fetchDevicesData()

        tempSlot.captured shouldBe sensorTemperature.toString()
        humSlot.captured shouldBe sensorHumidity.toString()
        tempAndMoreSlot.captured shouldBe sensorAndMoreTemperature.toString()
        humAndMoreSlot.captured shouldBe sensorAndMoreHumidity.toString()
        verify(exactly = 1) { cache.set("temp:switchbot:${sensor.providerId}", any()) }
        verify(exactly = 1) { cache.set("hum:switchbot:${sensor.providerId}", any()) }
        verify(exactly = 1) { cache.set("temp:switchbot:${sensorAndMore.providerId}", any()) }
        verify(exactly = 1) { cache.set("hum:switchbot:${sensorAndMore.providerId}", any()) }
    }

}
