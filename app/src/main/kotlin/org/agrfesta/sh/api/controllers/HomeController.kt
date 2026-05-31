package org.agrfesta.sh.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.agrfesta.sh.api.home.HomeStreamBroadcaster
import org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/home")
class HomeController(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val homeStreamBroadcaster: HomeStreamBroadcaster
) {

    @GetMapping
    fun getHome(): ResponseEntity<Any> =
        when (val result = getHomeDashboardUseCase.execute()) {
            is Right -> ok(result.value.toResponse())
            is Left -> internalServerError().body(MessageResponse("Unable to fetch home dashboard!"))
        }

    @GetMapping("/stream", produces = [TEXT_EVENT_STREAM_VALUE])
    fun getHomeStream(): SseEmitter = homeStreamBroadcaster.register()
}
