package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.diagnostics.InspectProviderUseCase
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.MissingProbeParams
import org.agrfesta.sh.api.core.domain.failures.ProviderDiagnosticsNotSupported
import org.agrfesta.sh.api.core.domain.failures.ProviderInspectionFailure
import org.agrfesta.sh.api.core.domain.failures.UnknownProbe
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.notFound
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/providers")
class ProvidersController(
    private val inspectProviderUseCase: InspectProviderUseCase
) {
    /**
     * ❗ Diagnostic contract, not an API contract: the payload is the provider's raw response,
     * carries no schema guarantee, and may change or disappear at any time.
     */
    @GetMapping("/{provider}/diagnostics")
    fun diagnostics(
        @PathVariable provider: String,
        @RequestParam probe: String,
        @RequestParam allParams: Map<String, String>
    ): ResponseEntity<Any> {
        val requestedProvider = Provider.entries.firstOrNull { it.name.equals(provider, ignoreCase = true) }
            ?: return notFound().build()
        val probeParams = allParams - "probe"
        return when (val result = inspectProviderUseCase.execute(requestedProvider, probe, probeParams)) {
            is Either.Right -> ok().contentType(MediaType.APPLICATION_JSON).body(result.value)
            is Either.Left -> when (val failure = result.value) {
                is UnknownProbe -> badRequest().body(
                    UnknownProbeResponse(
                        message = "Unknown probe '${failure.probe}' for provider '$requestedProvider'!",
                        availableProbes = failure.availableProbes
                    )
                )
                is MissingProbeParams -> badRequest().body(
                    MissingProbeParamsResponse(
                        message = "Probe '${failure.probe}' is missing required params!",
                        expectedParams = failure.expectedParams
                    )
                )
                ProviderDiagnosticsNotSupported -> status(HttpStatus.NOT_IMPLEMENTED)
                    .body(MessageResponse("Diagnostics is not available for provider '$requestedProvider'!"))
                is ProviderInspectionFailure -> status(HttpStatus.BAD_GATEWAY)
                    .body(
                        MessageResponse(
                            failure.message ?: "Provider diagnostics failed for provider '$requestedProvider'!"
                        )
                    )
            }
        }
    }
}

/** Self-describing 400 body: the hint carries the probes the provider actually supports. */
data class UnknownProbeResponse(
    val message: String,
    val availableProbes: Set<String>
)

/** Self-describing 400 body: the hint carries the full required param set of the probe. */
data class MissingProbeParamsResponse(
    val message: String,
    val expectedParams: Set<String>
)
