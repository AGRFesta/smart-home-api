package org.agrfesta.sh.api.utils

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceProviderIdentity
import org.slf4j.Logger
import org.springframework.stereotype.Service

@Service
class SmartCache(
    val cache: Cache,
    private val objectMapper: ObjectMapper
) {
    fun getThermoHygroOf(device: Device): Either<CacheFailure, ThermoHygroData> {
        val result = cache.get(device.getThermoHygroKey())
        return result.flatMap { parseThermoHygroData(it) }
    }

    fun setThermoHygroOf(device: Device, thermoHygro: ThermoHygroData) =
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
    "sensors:${provider.name.lowercase()}:${providerId}:thermohygro"

fun Either<CacheFailure, ThermoHygroData>.onLeftLogOn(logger: Logger) = onLeft {
    when(it) {
        is CacheError -> logger.error("ThermoHygroData cache fetch failure", it.reason)
        is CachedValueNotFound -> logger.error("missing cache key: ${it.key}")
    }
}

private data class ThermoHygroCacheEntry(val t: String, val h: String)
