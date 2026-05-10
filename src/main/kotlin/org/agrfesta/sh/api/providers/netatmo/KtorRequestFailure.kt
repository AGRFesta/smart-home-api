package org.agrfesta.sh.api.providers.netatmo

import io.ktor.http.HttpStatusCode
import org.agrfesta.sh.api.core.domain.failures.Failure

data class KtorRequestFailure(
    val failureStatusCode: HttpStatusCode,
    val body: String
): Failure
