package org.agrfesta.sh.api.core.domain.failures

/**
 * Failure of an operation on an actuator device (status fetch, on/off command).
 *
 * Deliberately non-sealed: outbound adapters in other modules (e.g. `:providers`) implement it
 * with provider-specific subtypes; callers handle it generically.
 */
interface ActuatorOperationFailure
