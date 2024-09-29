package org.agrfesta.sh.api.schedulers

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.SensorDataPersistenceSuccess
import org.agrfesta.sh.api.persistence.SensorsHistoryDataDao
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheError
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomHumidity
import org.agrfesta.test.mothers.aRandomTemperature
import org.junit.jupiter.api.Test
import java.time.Instant

class DevicesDataHistorySchedulerUnitTest {
    private val cache: Cache = mockk()
    private val timeService: TimeService = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val historyDataDao: SensorsHistoryDataDao = mockk()
    private val now = Instant.now()

    private val sut = DevicesDataHistoryScheduler(
        devicesDao = devicesDao,
        historyDataDao = historyDataDao,
        cache = cache,
        timeService = timeService
    )

    init {
        every { timeService.now() } returns now
    }

    @Test fun `historyDevicesData() do not save any history data when there are no devices`() {
        every { devicesDao.getAll() } returns emptyList()

        sut.historyDevicesData()

        verify(exactly = 0) { cache.get(any()) }
        verify(exactly = 0) { historyDataDao.persistHumidity(any(), any(),any()) }
        verify(exactly = 0) { historyDataDao.persistTemperature(any(), any(),any()) }
    }

    @Test fun `historyDevicesData() do not save any history data when there are no sensors`() {
        val noSensorDevice = aDevice(features = setOf(DeviceFeature.ACTUATOR))
        every { devicesDao.getAll() } returns listOf(noSensorDevice)

        sut.historyDevicesData()

        verify(exactly = 0) { cache.get(any()) }
        verify(exactly = 0) { historyDataDao.persistHumidity(any(), any(),any()) }
        verify(exactly = 0) { historyDataDao.persistTemperature(any(), any(),any()) }
    }

    @Test fun `historyDevicesData() ignores temperature cache fetch failures`() {
        val sensorA = aSensor()
        val sensorAHumidity = aRandomHumidity()
        val sensorB = aSensor()
        val sensorBTemp = aRandomTemperature()
        val sensorBHumidity = aRandomHumidity()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:temperature") } returns
                CacheError(Exception("cache fetch failure")).left()
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:humidity") } returns
                sensorAHumidity.asText().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:temperature") } returns
                sensorBTemp.toString().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:humidity") } returns
                sensorBHumidity.asText().right()
        every { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAHumidity) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) } returns
                SensorDataPersistenceSuccess.right()

        sut.historyDevicesData()

        verify(exactly = 1) { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAHumidity) }
        verify(exactly = 0) { historyDataDao.persistTemperature(sensorA.uuid, any(), any()) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) }
    }

    @Test fun `historyDevicesData() ignores humidity cache fetch failures`() {
        val sensorA = aSensor()
        val sensorATemp = aRandomTemperature()
        val sensorB = aSensor()
        val sensorBTemp = aRandomTemperature()
        val sensorBHumidity = aRandomHumidity()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:temperature") } returns
                sensorATemp.toString().right()
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:humidity") } returns
                CacheError(Exception("cache fetch failure")).left()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:temperature") } returns
                sensorBTemp.toString().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:humidity") } returns
                sensorBHumidity.asText().right()
        every { historyDataDao.persistTemperature(sensorA.uuid, now, sensorATemp) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) } returns
                SensorDataPersistenceSuccess.right()

        sut.historyDevicesData()

        verify(exactly = 0) { historyDataDao.persistHumidity(sensorA.uuid, any(), any()) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorA.uuid, now, sensorATemp) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) }
    }

    @Test fun `historyDevicesData() ignores temperature persist failures`() {
        val sensorA = aSensor()
        val sensorATemp = aRandomTemperature()
        val sensorAHumidity = aRandomHumidity()
        val sensorB = aSensor()
        val sensorBTemp = aRandomTemperature()
        val sensorBHumidity = aRandomHumidity()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:temperature") } returns
                sensorATemp.toString().right()
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:humidity") } returns
                sensorAHumidity.asText().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:temperature") } returns
                sensorBTemp.toString().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:humidity") } returns
                sensorBHumidity.asText().right()
        every { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAHumidity) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistTemperature(sensorA.uuid, now, sensorATemp) } returns
                PersistenceFailure(Exception("persistence failure")).left()
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) } returns
                SensorDataPersistenceSuccess.right()

        sut.historyDevicesData()

        verify(exactly = 1) { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorA.uuid, now, sensorATemp) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) }
    }

    @Test fun `historyDevicesData() ignores humidity persist failures`() {
        val sensorA = aSensor()
        val sensorATemp = aRandomTemperature()
        val sensorAHumidity = aRandomHumidity()
        val sensorB = aSensor()
        val sensorBTemp = aRandomTemperature()
        val sensorBHumidity = aRandomHumidity()
        every { devicesDao.getAll() } returns listOf(sensorA, sensorB)
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:temperature") } returns
                sensorATemp.toString().right()
        every { cache.get("sensors:${sensorA.provider.name.lowercase()}:${sensorA.providerId}:humidity") } returns
                sensorAHumidity.asText().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:temperature") } returns
                sensorBTemp.toString().right()
        every { cache.get("sensors:${sensorB.provider.name.lowercase()}:${sensorB.providerId}:humidity") } returns
                sensorBHumidity.asText().right()
        every { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAHumidity) } returns
                PersistenceFailure(Exception("persistence failure")).left()
        every { historyDataDao.persistTemperature(sensorA.uuid, now, sensorATemp) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) } returns
                SensorDataPersistenceSuccess.right()
        every { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) } returns
                SensorDataPersistenceSuccess.right()

        sut.historyDevicesData()

        verify(exactly = 1) { historyDataDao.persistHumidity(sensorA.uuid, now, sensorAHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorA.uuid, now, sensorATemp) }
        verify(exactly = 1) { historyDataDao.persistHumidity(sensorB.uuid, now, sensorBHumidity) }
        verify(exactly = 1) { historyDataDao.persistTemperature(sensorB.uuid, now, sensorBTemp) }
    }

}
