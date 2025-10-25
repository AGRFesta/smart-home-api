package org.agrfesta.sh.api.utils

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.DeviceProviderIdentity
import org.slf4j.Logger
import org.springframework.stereotype.Service

@Service
class SmartCache(
    private val cache: Cache,
    private val objectMapper: ObjectMapper
) {

    /**
     * Retrieves the cached thermo-hygrometric data for the specified [device].
     *
     * This method attempts to fetch a raw string representation of the thermo-hygrometric data
     * from the cache using a key derived from the given [device]. If the cached data exists
     * and is successfully retrieved, it is then parsed into a [ThermoHygroData] object.
     *
     * The result is wrapped in an [Either] type to represent success or failure:
     * - On success, it returns [Either.Right] containing the parsed [ThermoHygroData].
     * - On failure (e.g., missing cache entry or parsing error), it returns [Either.Left]
     *   with a [CacheFailure] describing the reason.
     *
     * @param device the [DeviceProviderIdentity] for which to retrieve thermo-hygrometric data.
     * @return an [Either] containing a [CacheFailure] on the left, or the [ThermoHygroData] on the right.
     */
    fun getThermoHygroOf(device: DeviceProviderIdentity): Either<CacheFailure, ThermoHygroData> {
        val result = cache.get(device.getThermoHygroKey())
        return result.flatMap { parseThermoHygroData(it) }
    }

    fun setThermoHygroOf(device: DeviceProviderIdentity, thermoHygro: ThermoHygroData) =
        cache.set(device.getThermoHygroKey(), objectMapper.writeValueAsString(thermoHygro.toThermoHygroCacheEntry()))

    private fun parseThermoHygroData(json: String): Either<CacheFailure, ThermoHygroData> {
        return try {
            val data = objectMapper.readValue(json, ThermoHygroCacheEntry::class.java).toThermoHygroData()
            data.right()
        } catch (e: Exception) {
            CacheError(e).left()
        }
    }

    private fun ThermoHygroData.toThermoHygroCacheEntry() = ThermoHygroCacheEntry(
        t = temperature.toString(),
        h = relativeHumidity.value.toString()
    )

    private fun ThermoHygroCacheEntry.toThermoHygroData() = ThermoHygroData(
        temperature = Temperature(t),
        relativeHumidity = RelativeHumidity(h)
    )
}

fun DeviceProviderIdentity.getThermoHygroKey() =
    "sensors:${provider.name.lowercase()}:${deviceProviderId}:thermohygro"

fun Either<CacheFailure, ThermoHygroData>.onLeftLogOn(logger: Logger) = onLeft {
    when(it) {
        is CacheError -> logger.error("ThermoHygroData cache fetch failure", it.exception)
        is CachedValueNotFound -> logger.error("missing cache key: ${it.key}")
    }
}

private data class ThermoHygroCacheEntry(val t: String, val h: String)
