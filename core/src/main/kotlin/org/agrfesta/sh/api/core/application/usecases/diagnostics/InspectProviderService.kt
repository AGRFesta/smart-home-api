package org.agrfesta.sh.api.core.application.usecases.diagnostics

import arrow.core.Either
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.inbounds.diagnostics.InspectProviderUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics.InspectableProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure
import org.agrfesta.sh.api.core.domain.failures.ProviderDiagnosticsNotSupported
import org.springframework.stereotype.Service

@Service
class InspectProviderService(
    // Kotlin default = optional dependency: no provider ships a diagnostics implementation
    // unless its conditional gate is enabled, and the context must still boot with none.
    private val inspectableProviders: Set<InspectableProvider> = emptySet()
) : InspectProviderUseCase {

    override fun execute(
        provider: Provider,
        probe: String,
        params: Map<String, String>
    ): Either<InspectProviderFailure, String> {
        val inspectable = inspectableProviders.firstOrNull { it.provider == provider }
            ?: return ProviderDiagnosticsNotSupported.left()
        return inspectable.inspect(probe, params)
    }
}
