package org.agrfesta.sh.api.utils

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.Humidity
import org.agrfesta.sh.api.domain.devices.Temperature

fun Device.getTemperatureKey(): String = "sensors:${provider.name.lowercase()}:${providerId}:temperature"
fun Device.getHumidityKey(): String = "sensors:${provider.name.lowercase()}:${providerId}:humidity"

fun Cache.getTemperatureOf(device: Device) = get(device.getTemperatureKey())
fun Cache.getHumidityOf(device: Device) = get(device.getHumidityKey())

fun Cache.setTemperatureOf(device: Device, temperature: Temperature) =
    set(device.getTemperatureKey(), temperature.toString())
fun Cache.setHumidityOf(device: Device, humidity: Humidity) = set(device.getHumidityKey(), humidity.asText())
