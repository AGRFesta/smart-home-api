package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either

interface Actuator: DeviceDriver

interface ActuatorOperationFailure

interface OnOffActuator: Actuator {
    fun getActuatorStatus(): Either<ActuatorOperationFailure, ActuatorStatus>
    fun on(): Either<ActuatorOperationFailure, Unit>
    fun off(): Either<ActuatorOperationFailure, Unit>
}

enum class ActuatorStatus {ON, OFF, UNDEFINED}
