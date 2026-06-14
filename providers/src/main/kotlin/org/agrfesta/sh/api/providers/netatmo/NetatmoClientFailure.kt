package org.agrfesta.sh.api.providers.netatmo

import org.agrfesta.sh.api.core.domain.devices.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.devices.SensorReadingsFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure

sealed interface NetatmoClientFailure : SensorReadingsFailure, ActuatorOperationFailure

data class NetatmoPropertyError(val failure: GetPropertyFailure) : NetatmoClientFailure

/**
 * Maps a [NetatmoClientFailure] to a clean [Exception] whose message is safe to surface to clients,
 * deliberately avoiding leaking raw response bodies or token context (e.g. [NetatmoContractBreak]
 * exposes only its message, not the captured response).
 */
internal fun NetatmoClientFailure.toException(): Exception = when (this) {
    is NetatmoAuthFailure -> exception
    is NetatmoPropertyError -> RuntimeException(failure.toString())
    is NetatmoInvalidAccessToken -> RuntimeException("Netatmo access token is not valid")
    is NetatmoContractBreak -> RuntimeException("Netatmo contract break: $message")
    is KtorRequestFailure -> RuntimeException("HTTP $failureStatusCode: $body")
}
