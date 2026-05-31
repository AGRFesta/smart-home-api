package org.agrfesta.sh.api.home

import org.agrfesta.sh.api.controllers.toResponse
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Inbound adapter broadcasting the home dashboard to connected SSE clients.
 *
 * On registration, it pushes the current state immediately; on each [HomeStateRefreshEvent] it re-fetches the dashboard
 * and broadcasts it to every connected client, dropping emitters whose client has disconnected.
 */
@Component
class HomeStreamBroadcaster(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val sseEmitterFactory: SseEmitterFactory
) {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    /**
     * Registers a new SSE connection and immediately sends the current home dashboard as the initial event.
     */
    fun register(): SseEmitter {
        val emitter = sseEmitterFactory.create()
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        val initialDashboard = getHomeDashboardUseCase.execute()
        synchronized(emitter) {
            initialDashboard.fold(
                ifLeft = { emitter.complete() },
                ifRight = { dashboard ->
                    emitters.add(emitter)
                    try {
                        emitter.send(SseEmitter.event().data(dashboard.toResponse()))
                    } catch (_: Exception) {
                        emitters.remove(emitter)
                    }
                }
            )
        }
        return emitter
    }

    /**
     * Re-fetches the dashboard and broadcasts it to every connected client.
     */
    @EventListener
    fun onHomeStateRefresh(event: HomeStateRefreshEvent) {
        getHomeDashboardUseCase.execute().onRight { dashboard ->
            broadcast(SseEmitter.event().data(dashboard.toResponse()))
        }
    }

    /**
     * Emits a comment-only keep-alive event to every connected client so idle connections are not closed by proxies.
     */
    @Scheduled(fixedRateString = "\${home.stream.keep-alive-ms:30000}")
    fun keepAlive() {
        broadcast(SseEmitter.event().comment("ping"))
    }

    /**
     * Sends [event] to every connected emitter, dropping any whose client has disconnected.
     */
    private fun broadcast(event: SseEmitter.SseEventBuilder) {
        emitters.forEach { emitter ->
            try {
                synchronized(emitter) {
                    emitter.send(event)
                }
            } catch (_: Exception) {
                emitters.remove(emitter)
            }
        }
    }
}
