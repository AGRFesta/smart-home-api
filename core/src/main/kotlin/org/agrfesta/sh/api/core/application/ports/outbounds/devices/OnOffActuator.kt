package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure

interface Actuator : DeviceDriver

interface OnOffActuator : Actuator {
    fun getActuatorStatus(): Either<ActuatorOperationFailure, ActuatorStatus>
    fun on(): Either<ActuatorOperationFailure, Unit>
    fun off(): Either<ActuatorOperationFailure, Unit>
}
