package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
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

    fun givenDevice(data: ProviderDeviceData, status: ThermoHygroData? = null) {
        cache.set("provider.netatmo.access-token", token)
        val homeData = objectMapper.aHomeData(
            name = data.name,
            deviceId = data.deviceProviderId
        )
        asserter.givenHomeDataFetchResponse(homeData)
        status?.apply {
            val homeStatus = aNetatmoHomeStatus(
                rooms = listOf(aNetatmoRoomStatus(
                    humidity = relativeHumidity.value.movePointRight(2).stripTrailingZeros(),
                    measuredTemperature = temperature.value
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
