package org.agrfesta.sh.api.core.domain.failures

import io.ktor.http.HttpStatusCode

interface Failure

data class MessageFailure(val message: String): Failure
interface ExceptionFailure: Failure { val exception: Exception}

data class KtorRequestFailure(
    val failureStatusCode: HttpStatusCode,
    val body: String
): Failure
