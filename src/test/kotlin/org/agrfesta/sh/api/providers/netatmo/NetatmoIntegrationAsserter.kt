package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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

    fun givenDevice(device: DeviceDataValue) {
        cache.set("provider.netatmo.access-token", token)
        val homeData = objectMapper.aHomeData(
            name = device.name,
            deviceId = device.deviceProviderId
        )
        asserter.givenHomeDataFetchResponse(homeData)
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
