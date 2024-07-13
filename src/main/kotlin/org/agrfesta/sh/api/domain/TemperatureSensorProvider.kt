package org.agrfesta.sh.api.domain

interface TemperatureSensorProvider {
    fun getTemperature(deviceProviderId: String): TemperatureSensorData
}