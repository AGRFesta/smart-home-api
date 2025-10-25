package org.agrfesta.sh.api.schedulers

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.services.SensorsHistoryDataService
import org.agrfesta.sh.api.utils.CacheError
import org.agrfesta.sh.api.utils.SmartCache
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomThermoHygroData
import org.junit.jupiter.api.Test

class DevicesDataHistorySchedulerUnitTest {
    private val devicesFactories: Collection<ProviderDevicesFactory> = listOf()

    private val smartCache: SmartCache = mockk()
    private val timeService: TimeService = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val historyDataDao: SensorsHistoryDataDao = mockk()
    private val now = Instant.now()

    private val sut = DevicesDataHistoryScheduler(
        devicesService = DevicesService(devicesDao, devicesFactories),
        historyDataService = SensorsHistoryDataService(historyDataDao),
        cache = smartCache,
        timeService = timeService
    )

    init {
        every { timeService.now() } returns now
    }

    @Test fun `historyDevicesData() do not save any history data when there are no devices`() {
        every { devicesDao.getAll() } returns emptyList()

        sut.historyDevicesData()

        verify(exactly = 0) { smartCache.getThermoHygroOf(any()) }
        verify(exactly = 0) { historyDataDao.persistHumidity(any(), any(),any()) }
        verify(exactly = 0) { historyDataDao.persistTemperature(any(), any(),any()) }
    }

    @Test fun `historyDevicesData() do not save any history data when there are no sensors`() {
        val noSensorDevice = aDevice(features = setOf(DeviceFeature.ACTUATOR))
        every { devicesDao.getAll() } returns listOf(noSensorDevice)

        sut.historyDevicesData()

        verify(exactly = 0) { smartCache.getThermoHygroOf(any()) }
        verify(exactly = 0) { historyDataDao.persistHumidity(any(), any(),any()) }
        verify(exactly = 0) { historyDataDao.persistTemperature(any(), any(),any()) }
    }

    @Test fun `historyDevicesData() ignores data cache fetch failures`() {
        val sensorA = aSensor()
        val sensorB = aSensor()
        val sensorBData = aRandomThermoHygroData()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { smartCache.getThermoHygroOf(sensorA) } returns CacheError(Exception("cache fetch failure")).left()
        every { smartCache.getThermoHygroOf(sensorB) } returns sensorBData.right()
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBData.relativeHumidity) } returns Unit
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBData.temperature) } returns Unit

        sut.historyDevicesData()

        verify(exactly = 0) { historyDataDao.persistHumidity(sensorA.uuid, any(), any()) }
        verify(exactly = 0) { historyDataDao.persistTemperature(sensorA.uuid, any(), any()) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBData.relativeHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBData.temperature) }
    }

    @Test fun `historyDevicesData() ignores temperature persist failures`() {
        val sensorA = aSensor()
        val sensorAData = aRandomThermoHygroData()
        val sensorB = aSensor()
        val sensorBData = aRandomThermoHygroData()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { smartCache.getThermoHygroOf(sensorA) } returns sensorAData.right()
        every { smartCache.getThermoHygroOf(sensorB) } returns sensorBData.right()
        every { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAData.relativeHumidity) } returns Unit
        every { historyDataDao.persistTemperature(sensorA.uuid, now, sensorAData.temperature) } returns Unit
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBData.relativeHumidity) } returns Unit
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBData.temperature) } returns Unit

        sut.historyDevicesData()

        verify(exactly = 1) { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAData.relativeHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorA.uuid, now, sensorAData.temperature) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBData.relativeHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBData.temperature) }
    }

    @Test fun `historyDevicesData() ignores humidity persist failures`() {
        val sensorA = aSensor()
        val sensorAData = aRandomThermoHygroData()
        val sensorB = aSensor()
        val sensorBData = aRandomThermoHygroData()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { smartCache.getThermoHygroOf(sensorA) } returns sensorAData.right()
        every { smartCache.getThermoHygroOf(sensorB) } returns sensorBData.right()
        every { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAData.relativeHumidity) } throws
                Exception("persistence failure")
        every { historyDataDao.persistTemperature(sensorA.uuid, now, sensorAData.temperature) } returns Unit
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBData.relativeHumidity) } returns Unit
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBData.temperature) } returns Unit

        sut.historyDevicesData()

        verify(exactly = 1) { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAData.relativeHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorA.uuid, now, sensorAData.temperature) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBData.relativeHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBData.temperature) }
    }

}
