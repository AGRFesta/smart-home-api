package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus

interface Actuator : DeviceDriver

interface ActuatorOperationFailure

interface OnOffActuator : Actuator {
    fun getActuatorStatus(): Either<ActuatorOperationFailure, ActuatorStatus>
    fun on(): Either<ActuatorOperationFailure, Unit>
    fun off(): Either<ActuatorOperationFailure, Unit>
}
