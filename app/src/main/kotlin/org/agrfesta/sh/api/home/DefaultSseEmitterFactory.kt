package org.agrfesta.sh.api.home

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Default [SseEmitterFactory] creating emitters with a configurable timeout (`home.stream.emitter-timeout-ms`,
 * default one hour).
 */
@Component
class DefaultSseEmitterFactory(
    @Value("\${home.stream.emitter-timeout-ms:3600000}") private val emitterTimeoutMs: Long
) : SseEmitterFactory {

    override fun create(): SseEmitter = SseEmitter(emitterTimeoutMs)
}
