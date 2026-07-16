package org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure

/**
 * Provider-level diagnostics capability: runs a named **read-only** probe against the provider's
 * cloud and returns the raw response body as a JSON [String].
 *
 * Probes are stringly-typed on purpose: this is a diagnostic surface, and the domain must not
 * model every provider's probe catalog. Implementations are self-describing through typed
 * failures — an unknown probe or a missing required parameter must surface the available
 * probes / expected parameters in the returned [InspectProviderFailure].
 *
 * ❗ **Read-only, forever**: a probe must never send commands or mutate provider state.
 * Implementations violating this break the port contract.
 */
interface InspectableProvider {

    /** The provider this diagnostics implementation belongs to. */
    val provider: Provider

    /** The names of the probes this implementation supports. */
    val probes: Set<String>

    /**
     * Runs the named read-only [probe] with the given [params] and returns the provider's raw
     * response body.
     *
     * Contract on the returned body: implementations must return the provider body **verbatim**
     * when it is valid JSON; a non-JSON 2xx body must be wrapped in a
     * `{"nonJsonBody": "<body>"}` envelope, so the endpoint's `application/json` promise holds
     * for every implementation.
     *
     * @return [Either.Right] with the raw JSON body,
     * or [Either.Left] with an [InspectProviderFailure] when the probe is unknown, a required
     * parameter is missing, or the provider errors.
     */
    fun inspect(probe: String, params: Map<String, String>): Either<InspectProviderFailure, String>
}
