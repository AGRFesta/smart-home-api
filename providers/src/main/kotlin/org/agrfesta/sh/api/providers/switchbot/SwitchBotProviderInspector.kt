package org.agrfesta.sh.api.providers.switchbot

import arrow.core.Either
import arrow.core.left
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics.InspectableProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.agrfesta.sh.api.providers.asJsonPayload
import org.springframework.stereotype.Service

/**
 * Provider-level diagnostics for SwitchBot: read-only probes backed by [SwitchBotDevicesClient],
 * returning the cloud's response body **verbatim** — no parse → re-serialize round trip — so the
 * real shapes can be re-captured in any environment.
 */
@Service
@ConditionalOnSwitchBot
class SwitchBotProviderInspector(
    private val client: SwitchBotDevicesClient
) : InspectableProvider {

    override val provider: Provider = Provider.SWITCHBOT

    override val probes: Set<String> = setOf("devices")

    override fun inspect(probe: String, params: Map<String, String>): Either<InspectProviderFailure, String> {
        if (probe !in probes) return UnknownProbe(probe, probes).left()
        return Either.catch { runBlocking { client.getDevicesRaw() } }
            .map { it.asJsonPayload() }
            .mapLeft { ProviderInspectionFailure(it.toString()) }
    }
}
