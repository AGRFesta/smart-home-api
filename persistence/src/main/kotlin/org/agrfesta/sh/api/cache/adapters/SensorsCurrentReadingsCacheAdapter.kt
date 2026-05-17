package org.agrfesta.sh.api.cache.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.cache.dto.ThermoHygroDataCacheDto
import org.agrfesta.sh.api.cache.dto.toCacheDto
import org.agrfesta.sh.api.cache.dto.toDomain
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheError
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheOkResponse
import org.agrfesta.sh.api.core.application.ports.outbounds.CachedValueNotFound
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupError
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupFailure
import org.agrfesta.sh.api.core.domain.failures.SensorReadingsSaveError
import org.agrfesta.sh.api.core.domain.failures.SensorReadingsSaveFailure
import org.springframework.stereotype.Service

@Service
class SensorsCurrentReadingsCacheAdapter(
    private val cache: Cache,
    private val objectMapper: ObjectMapper
) : SensorsCurrentReadingsRepository {

    override fun findBy(sensor: DeviceProviderIdentity): Either<ReadingsLookupFailure, ThermoHygroData?> =
        cache.get(sensor.getThermoHygroKey())
            .fold(
                { failure ->
                    when (failure) {
                        is CachedValueNotFound -> null.right()
                        is CacheError -> ReadingsLookupError(failure.exception).left()
                    }
                },
                { json -> deserialize(json) }
            )

    override fun save(sensor: DeviceProviderIdentity, data: ThermoHygroData): Either<SensorReadingsSaveFailure, Unit> {
        val json = try {
            objectMapper.writeValueAsString(data.toCacheDto())
        } catch (e: JsonProcessingException) {
            return SensorReadingsSaveError(e).left()
        }
        return when (val response = cache.set(sensor.getThermoHygroKey(), json)) {
            is CacheError -> SensorReadingsSaveError(response.exception).left()
            CacheOkResponse -> Unit.right()
        }
    }

    private fun deserialize(json: String): Either<ReadingsLookupFailure, ThermoHygroData?> =
        try {
            objectMapper.readValue(json, ThermoHygroDataCacheDto::class.java).toDomain().right()
        } catch (e: JsonProcessingException) {
            ReadingsLookupError(e).left()
        }
}

fun DeviceProviderIdentity.getThermoHygroKey() =
    "sensors:${provider.name.lowercase()}:$deviceProviderId:thermohygro"
