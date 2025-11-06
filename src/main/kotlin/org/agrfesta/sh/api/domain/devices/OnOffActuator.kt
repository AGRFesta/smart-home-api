package org.agrfesta.sh.api.domain.devices

import arrow.core.Either
import org.agrfesta.sh.api.domain.failures.Failure

interface OnOffActuator: Device {
    suspend fun on(): Either<Failure, Unit>
    suspend fun off(): Either<Failure, Unit>
}
