package org.agrfesta.sh.api.providers.netatmo

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.DevicesProvider
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.persistence.jdbc.repositories.CacheJdbcRepository
import org.agrfesta.sh.api.persistence.onLeftLogOn
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheError
import org.agrfesta.sh.api.utils.CachedValueNotFound
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class NetatmoService(
    private val cache: Cache,
    private val cacheRepository: CacheJdbcRepository,
    private val netatmoClient: NetatmoClient,
    private val objectMapper: ObjectMapper
): DevicesProvider {
    private val logger by LoggerDelegate()
    override val provider: Provider = NETATMO

    companion object {
        const val ACCESS_TOKEN_CACHE_KEY = "provider.netatmo.access-token"
        const val REFRESH_TOKEN_CACHE_KEY = "provider.netatmo.refresh-token"
    }

    override fun getAllDevices(): Either<Failure, Collection<DeviceDataValue>> = runBlocking {
        getToken().flatMap {
            netatmoClient.getHomesData(it).map { data ->
                data.at("/body/homes/0/modules")
                    .map { node -> objectMapper.treeToValue(node, NetatmoModule::class.java) }
                    .map { module ->
                        DeviceDataValue(
                            providerId = module.id,
                            provider = provider,
                            name = module.name,
                            features = setOf(SENSOR, ACTUATOR)
                        )
                    }
            }
        }
    }

    private suspend fun getToken(): Either<Failure, String> = cache.get(ACCESS_TOKEN_CACHE_KEY).fold(
            ifLeft = { failure ->
                when (failure) {
                    is CacheError -> failure.left()
                    is CachedValueNotFound -> fetchAndCacheNewToken()
                }
            },
            ifRight = { it.right() }
        )

    private suspend fun fetchAndCacheNewToken(): Either<Failure, String> =
        cacheRepository.findEntry(REFRESH_TOKEN_CACHE_KEY)
            .flatMap { netatmoClient.refreshToken(it.value) }
            .map { refreshResp ->
                cache.set(ACCESS_TOKEN_CACHE_KEY, refreshResp.accessToken)
                cacheRepository.upsert(REFRESH_TOKEN_CACHE_KEY, refreshResp.refreshToken)
                    .onLeftLogOn(logger)
                refreshResp.accessToken
            }

}
