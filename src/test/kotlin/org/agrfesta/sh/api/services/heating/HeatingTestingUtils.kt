package org.agrfesta.sh.api.services.heating

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.every
import java.math.BigDecimal
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.failures.MessageFailure
import org.agrfesta.test.mothers.aRandomTemperature

data class HeatingAreaContext(
    val hysteresis: BigDecimal,
    val area: HeatableArea
)

fun HeatingAreaContext.hasTempAsTarget(): Temperature {
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

fun HeatingAreaContext.hasTempAboveTargetRange(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature.minus(hysteresis.multiply(BigDecimal.TWO))
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

fun HeatingAreaContext.hasTempBelowTargetRange(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature.plus(hysteresis.multiply(BigDecimal.TWO))
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

fun HeatingAreaContext.hasTempInTargetRangeAboveTarget(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature.minus(hysteresis.divide(BigDecimal.TWO))
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

fun HeatingAreaContext.hasTempInTargetRangeBelowTarget(): Pair<Temperature, Temperature> {
    val temperature = aRandomTemperature()
    val targetTemperature = temperature.plus(hysteresis.divide(BigDecimal.TWO))
    defineTempStatus(temperature, targetTemperature)
    return temperature to targetTemperature
}

private fun HeatingAreaContext.defineTempStatus(currentTemp: Temperature, targetTemp: Temperature) {
    coEvery { area.getCurrentTemperature() } returns currentTemp.right()
    every { area.getCurrentTargetTemperature() } returns targetTemp
}
