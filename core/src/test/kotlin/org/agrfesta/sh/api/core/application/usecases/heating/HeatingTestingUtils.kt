package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.right
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
    // Real Either instances: production folds over these results, and a relaxed-mock Either is
    // neither Left nor Right (an exhaustive fold would throw NoWhenBranchMatchedException).
    every { heater.on() } returns Unit.right()
    every { heater.off() } returns Unit.right()
    every { factory.createDevice(dto) } returns heater
    return heater
}
