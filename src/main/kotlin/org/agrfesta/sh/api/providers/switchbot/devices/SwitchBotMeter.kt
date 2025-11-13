package org.agrfesta.sh.api.providers.switchbot.devices

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.math.BigDecimal
import java.util.*
import org.agrfesta.sh.api.domain.devices.FailureByException
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.devices.SensorReadingsFailure
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotSensorReadings

class SwitchBotMeter (
    override val uuid: UUID,
    override val provider: Provider,
    override val deviceProviderId: String,
    private val client: SwitchBotDevicesClient
): Sensor {

    override suspend fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings> =
        try{
            val jsonNode = client.getDeviceStatus(deviceProviderId)
            SwitchBotSensorReadings(
                temperature = jsonNode.at("/body/temperature").asText().let { BigDecimal(it) },
                humidityInt = jsonNode.at("/body/humidity").intValue(),
                batteryLevel = jsonNode.at("/body/battery").intValue())
                .toSensorReadings()
                .right()
        } catch (e: Exception) {
            FailureByException(e).left()
        }

}
