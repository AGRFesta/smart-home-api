package org.agrfesta.sh.api.providers.hon

import arrow.core.Either
import arrow.core.left
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.diagnostics.InspectableProvider
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.InspectProviderFailure
import org.agrfesta.sh.api.core.domain.failures.MissingProbeParams
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.springframework.stereotype.Service

/**
 * Provider-level diagnostics for hOn: read-only probes backed by [HonApiClient], returning the
 * cloud's response body **verbatim** — no parse → re-serialize round trip, no payload scoping —
 * so the real shapes can be re-captured in any environment.
 */
@Service
@ConditionalOnHon
class HonProviderInspector(
    private val client: HonApiClient
) : InspectableProvider {

    override val provider: Provider = Provider.HON

    private val requiredParams: Map<String, Set<String>> = mapOf(
        "appliance-list" to emptySet(),
        "context" to setOf("macAddress", "applianceType"),
        "commands" to setOf("macAddress", "applianceType", "applianceModelId", "code"),
        "appliance-model" to setOf("macAddress", "code"),
    )

    override val probes: Set<String> = requiredParams.keys

    override fun inspect(probe: String, params: Map<String, String>): Either<InspectProviderFailure, String> {
        val expectedParams = requiredParams[probe] ?: return UnknownProbe(probe, probes).left()
        if (!params.keys.containsAll(expectedParams)) {
            return MissingProbeParams(probe, expectedParams).left()
        }
        return runBlocking {
            when (probe) {
                "appliance-list" -> client.rawApplianceList()
                "context" -> client.rawContext(params.withDefaults("category" to "CYCLE"))
                "commands" -> client.rawCommands(
                    params.withDefaults("os" to HonConstants.OS, "appVersion" to HonConstants.APP_VERSION)
                )
                "appliance-model" -> client.rawApplianceModel(params)
                // Unreachable while catalog and dispatch agree: a probe added to requiredParams
                // without its branch must fail loudly, never fall through to a wrong-but-200 call.
                else -> error("Probe '$probe' is in the catalog but has no dispatch branch")
            }
        }
            .map { it.asJsonPayload() }
            .mapLeft { ProviderInspectionFailure(it.toString()) }
    }

    /**
     * The endpoint promises `application/json`: a body the cloud sent as non-JSON (e.g. an HTML
     * maintenance page) is wrapped in a JSON envelope instead of being dropped — on a diagnostic
     * surface the weird body is exactly what the caller wants to see.
     */
    private fun String.asJsonPayload(): String =
        runCatching { HON_OBJECT_MAPPER.readTree(this) }.fold(
            { this },
            { HON_OBJECT_MAPPER.createObjectNode().put("nonJsonBody", this).toString() },
        )

    /** Adds each default entry only when the caller has not provided that key. */
    private fun Map<String, String>.withDefaults(vararg defaults: Pair<String, String>): Map<String, String> =
        defaults.fold(this) { acc, (key, value) -> if (key in acc) acc else acc + (key to value) }
}
