package org.agrfesta.sh.api.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.AbstractIntegrationTest
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.netatmo.NetatmoIntegrationAsserter
import org.agrfesta.sh.api.providers.switchbot.SwitchBotClientAsserter
import org.agrfesta.sh.api.utils.CacheIntegrationAsserter
import org.agrfesta.test.mothers.aRandomIntHumidity
import org.agrfesta.test.mothers.aRandomThermoHygroData
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class SensorReadingsSyncServiceIntegrationTest(
    private val sut: SensorReadingsSyncService,
    private val devicesRepository: DevicesJdbcRepository,
    private val switchBotClientAsserter: SwitchBotClientAsserter,
    private val netatmoIntegrationAsserter: NetatmoIntegrationAsserter,
    private val cacheIntegrationAsserter: CacheIntegrationAsserter,
    private val objectMapper: ObjectMapper
): AbstractIntegrationTest() {

    @Test fun `fetchAndCacheSensorData() caches sensors device values only and ignores failures`() {
        //TODO this is a no features device that proves it will be not considered, once features will be deprecated
        // have no sense anymore, replace it with a no sensor device (at moment do not exist)
        val noSensorDevice = aDeviceDataValue(features = emptySet()).apply { devicesRepository.persist(UUID.randomUUID(), this) }

        val swbSensorData = aRandomThermoHygroData(
            relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity()))
        val swbSensor = aDeviceDataValue(provider = Provider.SWITCHBOT, features = setOf(SENSOR))
            .apply { devicesRepository.persist(UUID.randomUUID(), this) }
        switchBotClientAsserter.givenSensorData(swbSensor.deviceProviderId, swbSensorData)

        val swbFaultySensor = aDeviceDataValue(provider = Provider.SWITCHBOT, features = setOf(SENSOR))
            .apply { devicesRepository.persist(UUID.randomUUID(), this) }
        switchBotClientAsserter.givenSensorDataFailure(swbFaultySensor.deviceProviderId)

        val nttSensorData = aRandomThermoHygroData(
            relativeHumidity = Percentage.ofHundreds(aRandomIntHumidity()))
        val nttSensor = aDeviceDataValue(provider = Provider.NETATMO, features = setOf(SENSOR))
            .apply { devicesRepository.persist(UUID.randomUUID(), this) }
        netatmoIntegrationAsserter.givenDevice(nttSensor, nttSensorData)

        runBlocking { sut.fetchAndCacheSensorData() }

        cacheIntegrationAsserter.verifyContainsNoThermoHygroDataFrom(noSensorDevice)
        cacheIntegrationAsserter.verifyContainsThermoHygroDataFrom(swbSensor, swbSensorData)
        cacheIntegrationAsserter.verifyContainsNoThermoHygroDataFrom(swbFaultySensor)
        cacheIntegrationAsserter.verifyContainsThermoHygroDataFrom(nttSensor, nttSensorData)
    }

}
