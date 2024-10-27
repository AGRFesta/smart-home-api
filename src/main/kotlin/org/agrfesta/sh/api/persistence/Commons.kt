package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.slf4j.Logger

data class PersistenceFailure(val exception: Exception): GetDeviceFailure, GetRoomFailure, RoomCreationFailure

fun Either<PersistenceFailure, Any>.onLeftLogOn(logger: Logger) = onLeft {
    logger.error("persistence failure", it.exception)
}

object PersistenceSuccess
