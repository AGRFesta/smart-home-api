package org.agrfesta.sh.api.providers.switchbot

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DevicesProvider
import org.agrfesta.sh.api.domain.devices.FailureByException
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ProviderFailure
import org.agrfesta.sh.api.domain.devices.ReadableValuesDeviceProvider
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.devices.SensorReadingsFailure
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class SwitchBotService(
    private val devicesClient: SwitchBotDevicesClient,
    private val mapper: ObjectMapper
): DevicesProvider, ReadableValuesDeviceProvider {
    override val provider: Provider = Provider.SWITCHBOT

    override fun getAllDevices(): Either<ProviderFailure, Collection<DeviceDataValue>> = runBlocking {
            try {
                (devicesClient.getDevices().at("/body/deviceList") as ArrayNode)
                    .map { mapper.treeToValue(it, SwitchBotDevice::class.java) }
                    .map {
                        DeviceDataValue(
                            providerId = it.deviceId,
                            provider = Provider.SWITCHBOT,
                            name = it.deviceName,
                            features = it.deviceType.features
                        )
                    }.right()
            } catch (e: Exception) {
                ProviderFailure(e).left()
            }
        }

    override suspend fun fetchSensorReadings(deviceProviderId: String): Either<SensorReadingsFailure, SensorReadings> {
        val jsonNode = try{
            devicesClient.getDeviceStatus(deviceProviderId)
        } catch (e: Exception) {
            return FailureByException(e).left()
        }
        return SwitchBotSensorReadings(
            temperature = jsonNode.at("/body/temperature").asText().let { BigDecimal(it) },
            humidityInt = jsonNode.at("/body/humidity").intValue(),
            batteryLevel = jsonNode.at("/body/battery").intValue())
            .toSensorReadings()
            .right()
    }
}
