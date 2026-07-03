package org.agrfesta.sh.api.core.domain.failures

/**
 * Groups all causes of a failure evaluating the heating state.
 */
sealed interface EvaluateHeatingStateFailure

/** The `heating.enabled` flag could not be read: it is unknown whether heating should be driven. */
data object HeatingFlagUnavailable : EvaluateHeatingStateFailure

/** The device registry could not be fetched: no heatable area can be resolved. */
data object DevicesUnavailable : EvaluateHeatingStateFailure

/** The areas with their assigned devices could not be fetched: nothing can be evaluated. */
data object AreasUnavailable : EvaluateHeatingStateFailure
