package org.agrfesta.sh.api.providers.switchbot.devices

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.BatteryPowered
import org.agrfesta.sh.api.core.domain.devices.FailureByException
import org.agrfesta.sh.api.core.domain.devices.Inspectable
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SensorReadings
import org.agrfesta.sh.api.core.domain.devices.SensorReadingsFailure
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotSensorReadings
import java.util.*

class SwitchBotMeter(
    override val uuid: UUID,
    override val provider: Provider,
    override val deviceProviderId: String,
    private val client: SwitchBotDevicesClient
) : Sensor, Inspectable, BatteryPowered {

    private companion object {
        /** SwitchBot reports battery as a 0–100 integer percentage. */
        const val MAX_BATTERY_PERCENTAGE = 100
    }

    /**
     * The device status payload, memoized for this driver's lifetime. A driver instance lives for a
     * single polling cycle, during which both [fetchReadings] and [batteryLevel] are invoked: caching
     * the status lets both read from one `GET /devices/{id}/status` call instead of hitting the
     * rate-limited provider twice.
     */
    private var status: JsonNode? = null

    private fun deviceStatus(): JsonNode =
        status ?: runBlocking { client.getDeviceStatus(deviceProviderId) }.also { status = it }

    // Ktor ClientRequestException (not IOException) + Jackson errors require broad Exception catch
    @Suppress("TooGenericExceptionCaught")
    override fun batteryLevel(): Either<DevicesProviderFailure, Int> =
        try {
            val battery = deviceStatus().at("/body/battery")
            if (battery.isInt && battery.intValue() in 0..MAX_BATTERY_PERCENTAGE) {
                battery.intValue().right()
            } else {
                DevicesProviderError(IllegalStateException("Missing or out-of-range battery node: $battery")).left()
            }
        } catch (e: Exception) {
            DevicesProviderError(e).left()
        }

    // Ktor ClientRequestException (not IOException) requires broad Exception catch
    @Suppress("TooGenericExceptionCaught")
    override fun inspect(): Either<DevicesProviderFailure, String> =
        try {
            runBlocking { client.getDeviceStatusRaw(deviceProviderId) }.right()
        } catch (e: Exception) {
            DevicesProviderError(e).left()
        }

    // Ktor ClientRequestException (not IOException) + Jackson errors require broad Exception catch
    @Suppress("TooGenericExceptionCaught")
    override fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings> =
        try {
            val jsonNode = deviceStatus()
            SwitchBotSensorReadings(
                temperature = jsonNode.at("/body/temperature").asText().let { Temperature.of(it) },
                humidityInt = jsonNode.at("/body/humidity").intValue()
            )
                .toSensorReadings()
                .right()
        } catch (e: Exception) {
            FailureByException(e).left()
        }
}
