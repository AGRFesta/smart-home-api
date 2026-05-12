package org.agrfesta.sh.api.core.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.Failure

interface Actuator: DeviceDriver

interface OnOffActuator: Actuator {
    fun getActuatorStatus(): Either<Failure, ActuatorStatus>
    fun on(): Either<Failure, Unit>
    fun off(): Either<Failure, Unit>
}

enum class ActuatorStatus {ON, OFF, UNDEFINED}
