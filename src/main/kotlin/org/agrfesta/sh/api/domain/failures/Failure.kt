package org.agrfesta.sh.api.domain.failures

import io.ktor.http.HttpStatusCode

interface Failure

interface MessageFailure: Failure { val message: String }
interface ExceptionFailure: Failure { val exception: Exception}

data class KtorRequestFailure(
    val failureStatusCode: HttpStatusCode,
    val body: String
): Failure
