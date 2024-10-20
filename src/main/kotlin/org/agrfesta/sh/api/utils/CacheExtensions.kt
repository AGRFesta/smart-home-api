package org.agrfesta.sh.api.utils

import org.agrfesta.sh.api.domain.commons.RelativeHumidity
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.Device

fun Device.getTemperatureKey(): String = "sensors:${provider.name.lowercase()}:${providerId}:temperature"
fun Device.getHumidityKey(): String = "sensors:${provider.name.lowercase()}:${providerId}:humidity"

fun Cache.setTemperatureOf(device: Device, temperature: Temperature) =
    set(device.getTemperatureKey(), temperature.toString())
fun Cache.setHumidityOf(device: Device, relativeHumidity: RelativeHumidity) =
    set(device.getHumidityKey(), relativeHumidity.asText())

fun Cache.getTemperatureOf(device: Device) = get(device.getTemperatureKey())
fun Cache.getRelativeHumidityOf(device: Device) = get(device.getHumidityKey())
