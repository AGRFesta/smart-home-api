package org.agrfesta.sh.api.providers.netatmo

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.DevicesProvider
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.services.PersistedCacheService
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class NetatmoService(
    private val cache: Cache,
    private val cacheService: PersistedCacheService,
    private val netatmoClient: NetatmoClient,
    private val objectMapper: ObjectMapper
): DevicesProvider {
    private val logger by LoggerDelegate()
    override val provider: Provider = NETATMO

    companion object {
        const val NETATMO_ACCESS_TOKEN_CACHE_KEY = "provider.netatmo.access-token"
        const val NETATMO_REFRESH_TOKEN_CACHE_KEY = "provider.netatmo.refresh-token"
    }

    override fun getAllDevices(): Either<Failure, Collection<DeviceDataValue>> = runBlocking {
        netatmoClient.getHomesData().map { data ->
            data.at("/body/homes/0/modules")
                .map { node -> objectMapper.treeToValue(node, NetatmoModuleData::class.java) }
                .map { module ->
                    DeviceDataValue(
                        deviceProviderId = module.id,
                        provider = provider,
                        name = module.name,
                        features = setOf(SENSOR, ACTUATOR)
                    )
                }
        }
    }

}
