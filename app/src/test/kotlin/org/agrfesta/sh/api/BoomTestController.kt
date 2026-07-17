package org.agrfesta.sh.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only controller, component-scanned into the integration context (it lives on the test
 * classpath under the application root package): a deterministic unhandled exception, needed to
 * exercise the container ERROR dispatch — something MockMvc cannot reproduce.
 */
@RestController
class BoomTestController {

    @GetMapping("/test-support/boom")
    fun boom(): Nothing = error("boom")
}
