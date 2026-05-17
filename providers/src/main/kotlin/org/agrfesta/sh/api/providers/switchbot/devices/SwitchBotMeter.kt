package org.agrfesta.sh.api.providers.switchbot.devices

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.FailureByException
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SensorReadings
import org.agrfesta.sh.api.core.domain.devices.SensorReadingsFailure
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotSensorReadings
import java.util.*

class SwitchBotMeter(
    override val uuid: UUID,
    override val provider: Provider,
    override val deviceProviderId: String,
    private val client: SwitchBotDevicesClient
) : Sensor {

    // Ktor ClientRequestException (not IOException) + Jackson errors require broad Exception catch
    @Suppress("TooGenericExceptionCaught")
    override fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings> =
        try {
            val jsonNode = runBlocking { client.getDeviceStatus(deviceProviderId) }
            SwitchBotSensorReadings(
                temperature = jsonNode.at("/body/temperature").asText().let { Temperature.of(it) },
                humidityInt = jsonNode.at("/body/humidity").intValue(),
                batteryLevel = jsonNode.at("/body/battery").intValue()
            )
                .toSensorReadings()
                .right()
        } catch (e: Exception) {
            FailureByException(e).left()
        }
}
