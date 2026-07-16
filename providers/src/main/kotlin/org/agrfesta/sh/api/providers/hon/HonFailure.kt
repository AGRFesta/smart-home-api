package org.agrfesta.sh.api.providers.hon

sealed interface HonFailure

sealed interface HonAuthFailure : HonFailure

/** The login flow broke at some step: page shape changed, unexpected status, etc. */
data class HonLoginFlowBroken(val reason: String) : HonAuthFailure

/** The account has email-OTP 2FA enabled: the flow cannot proceed unattended. */
data object HonMfaRequired : HonAuthFailure

/** The refresh_token grant was rejected: callers fall back to the full login. */
data class HonRefreshFailed(val statusCode: Int) : HonAuthFailure

/** The cloud rejected a command (`resultCode != "0"` on `/commands/v1/send`). */
data object HonCommandRejected : HonFailure

/** A request kept returning 401/403 even after a full re-authentication. */
data object HonUnauthorized : HonFailure

/** The cloud answered with a non-2xx status (other than 401/403) — a genuine server error. */
data class HonServerError(val statusCode: Int) : HonFailure

/** A network-level failure (timeout, unknown host, connection reset) talking to the cloud. */
data class HonNetworkError(val reason: String?) : HonFailure

/** The cloud answered with a body that is not valid JSON. */
data object HonNonJsonResponse : HonFailure
