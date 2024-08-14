package org.agrfesta.sh.api.domain.devices

data class TemperatureSensorData(
    val temperature: Double,
    val battery: Double? = null
)
