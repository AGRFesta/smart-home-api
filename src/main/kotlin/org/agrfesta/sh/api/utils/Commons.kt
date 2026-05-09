package org.agrfesta.sh.api.utils

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.slf4j.Logger

fun Either<PersistenceFailure, Any>.onLeftLogOn(logger: Logger) = onLeft {
    logger.error("persistence failure", it.exception)
}
