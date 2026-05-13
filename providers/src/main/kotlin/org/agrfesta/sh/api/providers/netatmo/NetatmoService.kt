package org.agrfesta.sh.api.providers.netatmo

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.core.domain.failures.Failure
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class NetatmoService(
    private val cache: Cache,
    private val netatmoClient: NetatmoClient,
    private val objectMapper: ObjectMapper
): DevicesProvider {
    private val logger by LoggerDelegate()
    override val provider: Provider = NETATMO

    companion object {
        const val NETATMO_ACCESS_TOKEN_CACHE_KEY = "provider.netatmo.access-token"
        const val NETATMO_REFRESH_TOKEN_CACHE_KEY = "provider.netatmo.refresh-token"
    }

    override fun getAllDevices(): Either<Failure, Collection<ProviderDeviceData>> = runBlocking {
        netatmoClient.getHomesData().map { data ->
            data.at("/body/homes/0/modules")
                .map { node -> objectMapper.treeToValue(node, NetatmoModuleData::class.java) }
                .map { module ->
                    ProviderDeviceData(
                        deviceProviderId = module.id,
                        provider = provider,
                        name = module.name,
                        features = setOf(SENSOR, ACTUATOR)
                    )
                }
        }
    }

}
