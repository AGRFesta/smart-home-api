package org.agrfesta.sh.api.domain

data class TemperatureSensorData(
    val temperature: Double,
    val battery: Double? = null
)
