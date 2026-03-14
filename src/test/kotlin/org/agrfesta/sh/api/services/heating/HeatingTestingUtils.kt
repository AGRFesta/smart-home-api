package org.agrfesta.sh.api.services.heating

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.every
import java.math.BigDecimal
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.failures.MessageFailure
import org.agrfesta.sh.api.schedulers.HeatingControlScheduler.Companion.HYSTERESIS
import org.agrfesta.test.mothers.aRandomTemperature

fun HeatableArea.hasTempAsTarget(): Temperature {
    val temperature = aRandomTemperature()
    defineTempStatus(temperature, temperature)
    return temperature
}

fun HeatableArea.hasUnavailableCurrentTemp() {
    val failure = MessageFailure("fetch failure")
    coEvery { getCurrentTemperature() } returns failure.left()
    every { getCurrentTargetTemperature() } returns aRandomTemperature()
}

fun HeatableArea.hasNoTargetTemp() {
    every { getCurrentTargetTemperature() } returns null
}

fun HeatableArea.hasTempAboveTargetRange(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature - HYSTERESIS.times(BigDecimal.TWO)
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

fun HeatableArea.hasTempBelowTargetRange(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature + HYSTERESIS.times(BigDecimal.TWO)
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

fun HeatableArea.hasTempInTargetRangeAboveTarget(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature - HYSTERESIS.div(BigDecimal.TWO)
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

fun HeatableArea.hasTempInTargetRangeBelowTarget(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature + HYSTERESIS.div(BigDecimal.TWO)
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

private fun HeatableArea.defineTempStatus(currentTemp: Temperature, targetTemp: Temperature) {
    coEvery { getCurrentTemperature() } returns currentTemp.right()
    every { getCurrentTargetTemperature() } returns targetTemp
}
