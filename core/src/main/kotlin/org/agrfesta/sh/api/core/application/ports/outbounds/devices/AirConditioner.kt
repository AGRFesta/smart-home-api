package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure

/**
 * An air conditioner: an on/off actuator that can additionally be driven on operating mode,
 * target temperature and fan speed. Each setter is a complete, self-contained command: the
 * driver is responsible for composing it with the device's current state when the underlying
 * protocol transmits full-state writes (as hOn's `settings` command does).
 */
interface AirConditioner : OnOffActuator {
    fun setMode(mode: AcMode): Either<ActuatorOperationFailure, Unit>
    fun setTargetTemperature(temperature: Temperature): Either<ActuatorOperationFailure, Unit>
    fun setFanSpeed(speed: AcFanSpeed): Either<ActuatorOperationFailure, Unit>
}
