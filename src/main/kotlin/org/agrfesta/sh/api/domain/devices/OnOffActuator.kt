package org.agrfesta.sh.api.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.domain.failures.Failure

interface Actuator: Device

interface OnOffActuator: Actuator {
    suspend fun getActuatorStatus(): Either<Failure, ActuatorStatus>
    suspend fun on(): Either<Failure, Unit>
    suspend fun off(): Either<Failure, Unit>
}

enum class ActuatorStatus {ON, OFF, UNDEFINED}
