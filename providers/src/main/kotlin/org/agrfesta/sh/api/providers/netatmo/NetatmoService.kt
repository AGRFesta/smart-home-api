package org.agrfesta.sh.api.providers.netatmo

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure
import org.springframework.stereotype.Service

@Service
@ConditionalOnNetatmo
class NetatmoService(
    private val netatmoClient: NetatmoClient
) : DevicesProvider {
    private val objectMapper = NETATMO_OBJECT_MAPPER
    override val provider: Provider = NETATMO

    companion object {
        const val NETATMO_ACCESS_TOKEN_CACHE_KEY = "provider.netatmo.access-token"
        const val NETATMO_REFRESH_TOKEN_CACHE_KEY = "provider.netatmo.refresh-token"
    }

    override fun getAllDevices(): Either<DevicesProviderFailure, Collection<ProviderDeviceData>> = runBlocking {
        netatmoClient.getHomesData()
            .mapLeft { DevicesProviderError(it.toException()) }
            .map { data ->
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
