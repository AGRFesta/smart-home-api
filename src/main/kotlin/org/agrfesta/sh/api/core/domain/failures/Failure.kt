package org.agrfesta.sh.api.core.domain.failures

import io.ktor.http.HttpStatusCode

@Deprecated("too generic definition choose a more specific")
interface Failure

data class MessageFailure(val message: String): Failure
interface ExceptionFailure: Failure { val exception: Exception}

data class KtorRequestFailure(
    val failureStatusCode: HttpStatusCode,
    val body: String
): Failure
