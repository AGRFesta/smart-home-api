package org.agrfesta.sh.api.providers.switchbot

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure
import org.springframework.stereotype.Service

@Service
@ConditionalOnSwitchBot
class SwitchBotService(
    private val devicesClient: SwitchBotDevicesClient,
    private val mapper: ObjectMapper
) : DevicesProvider {
    override val provider: Provider = Provider.SWITCHBOT

    // Ktor ClientRequestException (not IOException) + Jackson errors require broad Exception catch
    @Suppress("TooGenericExceptionCaught")
    override fun getAllDevices(): Either<DevicesProviderFailure, Collection<ProviderDeviceData>> = runBlocking {
        try {
            (devicesClient.getDevices().at("/body/deviceList") as ArrayNode)
                .map { mapper.treeToValue(it, SwitchBotDevice::class.java) }
                .map {
                    ProviderDeviceData(
                        deviceProviderId = it.deviceId,
                        provider = Provider.SWITCHBOT,
                        name = it.deviceName,
                        features = it.deviceType.features
                    )
                }.right()
        } catch (e: Exception) {
            DevicesProviderError(e).left()
        }
    }

//    override suspend fun fetchSensorReadings(deviceProviderId: String): Either<SensorReadingsFailure, SensorReadings> =
//        try{
//            val jsonNode = devicesClient.getDeviceStatus(deviceProviderId)
//            SwitchBotSensorReadings(
//                temperature = jsonNode.at("/body/temperature").asText().let { BigDecimal(it) },
//                humidityInt = jsonNode.at("/body/humidity").intValue(),
//                batteryLevel = jsonNode.at("/body/battery").intValue())
//                .toSensorReadings()
//                .right()
//        } catch (e: Exception) {
//            FailureByException(e).left()
//        }
}
