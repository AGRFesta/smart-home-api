package org.agrfesta.sh.api.home

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Creates [SseEmitter] instances for the home stream.
 *
 * Extracted as a seam so the emitter lifecycle can be controlled in tests (the concrete timeout and the `send` calls
 * are otherwise awkward to drive on real emitters).
 */
interface SseEmitterFactory {

    /**
     * Creates a new [SseEmitter] configured with the home-stream timeout.
     */
    fun create(): SseEmitter
}
