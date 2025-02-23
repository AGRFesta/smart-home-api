package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.slf4j.Logger

fun Either<PersistenceFailure, Any>.onLeftLogOn(logger: Logger) = onLeft {
    logger.error("persistence failure", it.exception)
}

object PersistenceSuccess
