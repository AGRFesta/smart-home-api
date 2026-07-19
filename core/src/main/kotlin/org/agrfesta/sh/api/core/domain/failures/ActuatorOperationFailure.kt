package org.agrfesta.sh.api.core.domain.failures

/**
 * Failure of an operation on an actuator device (status fetch, on/off command).
 *
 * Deliberately non-sealed: outbound adapters in other modules (e.g. `:providers`) implement it
 * with provider-specific subtypes; callers handle it generically.
 */
interface ActuatorOperationFailure {
    /**
     * Optional human-readable cause, suitable to surface to API clients. Null when the
     * adapter has nothing better than its type name — callers fall back to a generic text,
     * never to the debug `toString()`.
     */
    val message: String? get() = null
}

/**
 * An [ActuatorOperationFailure] caused by the requested setting being rejected by the
 * device's validation (value not admitted, out of range, unknown parameter): the request is
 * wrong, not the transport. Deliberately non-sealed like its parent — provider adapters
 * implement it with their own subtypes; callers only rely on [reason].
 */
interface AcSettingRejected : ActuatorOperationFailure {
    /** Human-readable explanation of the rejection, suitable to surface to API clients. */
    val reason: String
}
