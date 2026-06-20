package org.agrfesta.sh.api.core.application.usecases.heating

import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Sensor
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.SharedHeater
import org.agrfesta.sh.api.core.domain.devices.Device

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
