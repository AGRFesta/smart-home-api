package org.agrfesta.sh.api.cache.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.core.application.ports.outbounds.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupError
import org.agrfesta.sh.api.core.domain.failures.ReadingsLookupFailure
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.CacheError
import org.agrfesta.sh.api.utils.CachedValueNotFound
import org.agrfesta.sh.api.utils.getThermoHygroKey
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

    private fun deserialize(json: String): Either<ReadingsLookupFailure, ThermoHygroData?> =
        try {
            objectMapper.readValue(json, ThermoHygroData::class.java).right()
        } catch (e: Exception) {
            ReadingsLookupError(e).left()
        }

}
