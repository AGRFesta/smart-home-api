package org.agrfesta.sh.api.providers.netatmo

import io.ktor.http.HttpStatusCode

data class KtorRequestFailure(
    val failureStatusCode: HttpStatusCode,
    val body: String
) : NetatmoClientFailure
