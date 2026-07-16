package org.agrfesta.sh.api.core.application.ports.inbounds.diagnostics

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure

interface InspectProviderUseCase {

    /**
     * Routes the named read-only diagnostic [probe] to the [provider]'s diagnostics
     * implementation and returns the provider's raw response body.
     *
     * This is a diagnostic passthrough, never persisted nor cached: it intentionally surfaces
     * provider failures instead of masking them, and the returned payload carries **no schema
     * guarantee** — it is the provider's raw truth at the time of the call.
     *
     * @param provider the provider to probe.
     * @param probe the name of the probe to run.
     * @param params the probe's parameters, passed through as-is.
     * @return [Either.Right] with the provider's raw JSON body,
     * or [Either.Left] with an [InspectProviderFailure].
     */
    fun execute(
        provider: Provider,
        probe: String,
        params: Map<String, String>
    ): Either<InspectProviderFailure, String>
}
