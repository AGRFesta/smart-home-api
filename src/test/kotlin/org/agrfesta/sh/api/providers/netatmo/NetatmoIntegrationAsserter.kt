package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.test.mothers.aRandomUniqueString
import org.springframework.stereotype.Service

@Service
class NetatmoIntegrationAsserter(
    private val cache: Cache,
    private val asserter: NetatmoClientAsserter,
    private val objectMapper: ObjectMapper
) {
    val token = aRandomUniqueString()

    fun clear() {
        //TODO clear cache
        asserter.clear()
    }

    fun givenDevice(data: DeviceDataValue, status: ThermoHygroData? = null) {
        cache.set("provider.netatmo.access-token", token)
        val homeData = objectMapper.aHomeData(
            name = data.name,
            deviceId = data.deviceProviderId
        )
        asserter.givenHomeDataFetchResponse(homeData)
        status?.apply {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(aNetatmoRoomStatus(
                    humidity = relativeHumidity.toHundreds(),
                    measuredTemperature = temperature
                ))
            )
            asserter.givenHomeStatusFetchResponse(homeStatus)
        }
    }

    fun givenNoDevices() {
        cache.set("provider.netatmo.access-token", token)
        val homeData = objectMapper.anEmptyHomeData()
        asserter.givenHomeDataFetchResponse(homeData)
    }

    fun givenHomeDataFetchFailure() {
        cache.set("provider.netatmo.access-token", token)
        asserter.givenHomeDataFetchFailure()
    }

}
