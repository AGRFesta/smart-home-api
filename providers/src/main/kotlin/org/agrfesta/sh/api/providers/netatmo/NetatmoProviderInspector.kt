package org.agrfesta.sh.api.providers.netatmo

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics.InspectableProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure
import org.agrfesta.sh.api.core.domain.failures.MissingProbeParams
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.agrfesta.sh.api.providers.asJsonPayload
import org.springframework.stereotype.Service

/**
 * Provider-level diagnostics for Netatmo: read-only probes backed by [NetatmoClient], returning
 * the cloud's response body **verbatim** — no parse → re-serialize round trip, no room scoping —
 * so the real shapes can be re-captured in any environment.
 */
@Service
@ConditionalOnNetatmo
class NetatmoProviderInspector(
    private val client: NetatmoClient
) : InspectableProvider {

    override val provider: Provider = Provider.NETATMO

    override val probes: Set<String> = setOf("home-status")

    override fun inspect(probe: String, params: Map<String, String>): Either<InspectProviderFailure, String> {
        if (probe !in probes) return UnknownProbe(probe, probes).left()
        val homeId = params["homeId"] ?: return MissingProbeParams(probe, setOf("homeId")).left()
        return Either.catch { runBlocking { client.getHomeStatusRaw(homeId) } }
            .mapLeft { ProviderInspectionFailure(it.toString()) }
            .flatMap { outcome ->
                outcome
                    .map { it.asJsonPayload() }
                    .mapLeft { ProviderInspectionFailure(it.toString()) }
            }
    }
}
