package org.agrfesta.sh.api.cache.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheError
import org.agrfesta.sh.api.core.application.ports.outbounds.CacheOkResponse
import org.agrfesta.sh.api.core.application.ports.outbounds.CachedValueNotFound
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceBatteryRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceProviderIdentity
import org.agrfesta.sh.api.core.domain.failures.BatteryLookupError
import org.agrfesta.sh.api.core.domain.failures.BatteryLookupFailure
import org.agrfesta.sh.api.core.domain.failures.BatterySaveError
import org.agrfesta.sh.api.core.domain.failures.BatterySaveFailure
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import kotlin.time.toKotlinDuration

@Service
class DeviceBatteryCacheAdapter(
    private val cache: Cache,
    @Value("\${smart-home.cache.battery-ttl:5m}") private val ttl: Duration
) : DeviceBatteryRepository {

    override fun findBy(device: DeviceProviderIdentity): Either<BatteryLookupFailure, Int?> =
        cache.get(device.getBatteryKey())
            .fold(
                { failure ->
                    when (failure) {
                        is CachedValueNotFound -> null.right()
                        is CacheError -> BatteryLookupError(failure.exception).left()
                    }
                },
                { cached -> parseBatteryLevel(cached) }
            )

    override fun save(device: DeviceProviderIdentity, batteryLevel: Int): Either<BatterySaveFailure, Unit> =
        when (val response = cache.set(device.getBatteryKey(), batteryLevel.toString(), ttl.toKotlinDuration())) {
            is CacheError -> BatterySaveError(response.exception).left()
            CacheOkResponse -> Unit.right()
        }

    private fun parseBatteryLevel(cached: String): Either<BatteryLookupFailure, Int?> =
        try {
            cached.toInt().right()
        } catch (e: NumberFormatException) {
            BatteryLookupError(e).left()
        }
}

fun DeviceProviderIdentity.getBatteryKey() =
    "devices:${provider.name.lowercase()}:$deviceProviderId:battery"
