package org.agrfesta.sh.api.core.domain.failures

/**
 * Groups all causes of a failure running a diagnostic probe against a provider.
 */
sealed interface InspectProviderFailure

/** No provider-level diagnostics implementation is registered for the requested provider. */
data object ProviderDiagnosticsNotSupported : InspectProviderFailure

/** The requested probe is not in the provider's catalog; [availableProbes] is the hint. */
data class UnknownProbe(
    val probe: String,
    val availableProbes: Set<String>
) : InspectProviderFailure

/** A required parameter of the probe is missing; [expectedParams] is the full required set. */
data class MissingProbeParams(
    val probe: String,
    val expectedParams: Set<String>
) : InspectProviderFailure

/**
 * The provider was reached but failed or is unreachable; [message] surfaces the cause.
 *
 * [message] is diagnostic text (typically the transport failure's string form): it carries no
 * stability guarantee and must never be parsed.
 */
data class ProviderInspectionFailure(val message: String?) : InspectProviderFailure
