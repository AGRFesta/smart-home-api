package org.agrfesta.sh.api.domain.devices

interface TemperatureSensorProvider {
    fun getTemperature(deviceProviderId: String): TemperatureSensorData
}