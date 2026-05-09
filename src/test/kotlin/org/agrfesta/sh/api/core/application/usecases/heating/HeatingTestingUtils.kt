package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.agrfesta.sh.api.core.domain.failures.MessageFailure
import org.agrfesta.sh.api.core.application.usecases.heating.AbstractSharedHeatingAreasStrategyService.Companion.HYSTERESIS
import org.agrfesta.test.mothers.aRandomTemperature

fun Device.toSensorMockk(factory: ProviderDevicesFactory): Sensor {
    val dto = this
    val sensor: Sensor = mockk()
    every { sensor.uuid } returns uuid
    every { factory.createDevice(dto) } returns sensor
    return sensor
}

fun Device.toSharedHeaterMockk(factory: ProviderDevicesFactory): SharedHeater {
    val dto = this
    val heater: SharedHeater = mockk(relaxed = true)
    every { heater.uuid } returns uuid
    every { factory.createDevice(dto) } returns heater
    return heater
}

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
