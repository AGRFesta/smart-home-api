package org.agrfesta.sh.api.providers.switchbot

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.DevicesProvider
import org.agrfesta.sh.api.domain.devices.FailureByException
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.ReadableValuesDeviceProvider
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.devices.SensorReadingsFailure
import org.springframework.stereotype.Service

@Service
class SwitchBotService(
    private val devicesClient: SwitchBotDevicesClient,
    private val mapper: ObjectMapper
): DevicesProvider, ReadableValuesDeviceProvider {

    override fun getAllDevices(): Collection<Device> {
        return runBlocking {
            val response = devicesClient.getDevices()
            val devices = response.at("/body/deviceList") as ArrayNode
            devices
                .map { mapper.treeToValue(it, SwitchBotDevice::class.java) }
                .map {
                    Device(
                        providerId = it.deviceId,
                        provider = Provider.SWITCHBOT,
                        status = DeviceStatus.PAIRED,
                        name = it.deviceName,
                        features = it.deviceType.features
                    )
                }
        }
    }

    override suspend fun fetchSensorReadings(deviceProviderId: String): Either<SensorReadingsFailure, SensorReadings> {
        val jsonNode = try{
            devicesClient.getDeviceStatus(deviceProviderId)
        } catch (e: Exception) {
            return FailureByException(e).left()
        }
        return SwitchBotSensorReadings(
            temperature = jsonNode.at("/body/temperature").floatValue(),
            humidity = jsonNode.at("/body/humidity").intValue(),
            battery = jsonNode.at("/body/battery").intValue()
        ).right()
    }
}
