package org.agrfesta.sh.api.providers.hon.devices

import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.providers.hon.HonFailure

/** Failures of the hOn AC write path, surfaced to callers as [ActuatorOperationFailure]. */
sealed interface HonAcFailure : ActuatorOperationFailure

/** The requested value is not among the enum values the device firmware admits. */
data class AcSettingNotSupported(
    val parameter: String,
    val value: String,
    val allowed: List<String>,
) : HonAcFailure

/** The requested value is outside the parameter's range or off its min/step grid. */
data class AcSettingOutOfRange(
    val parameter: String,
    val value: String,
) : HonAcFailure

/** The requested parameter does not exist in the device's `settings` command catalog. */
data class AcUnknownParameter(val parameter: String) : HonAcFailure

/** The hOn transport failed (auth, network, server error, or the cloud rejected the command). */
data class HonAcProviderFailure(val failure: HonFailure) : HonAcFailure

/**
 * The appliance is not in the [org.agrfesta.sh.api.providers.hon.HonApplianceStore] yet: the
 * store is in-memory and empty after a restart until the device sync runs.
 */
data object AcApplianceNotSynced : HonAcFailure
