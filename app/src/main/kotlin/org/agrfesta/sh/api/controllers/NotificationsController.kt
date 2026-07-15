package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.notifications.GetNotificationsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
class NotificationsController(
    private val getNotificationsUseCase: GetNotificationsUseCase
) {

    @GetMapping
    fun getNotifications(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        if (page !in 0..MAX_PAGE || size !in 1..MAX_PAGE_SIZE) {
            return badRequest()
                .body(MessageResponse("page must be between 0 and $MAX_PAGE and size between 1 and $MAX_PAGE_SIZE!"))
        }
        return when (val result = getNotificationsUseCase.execute(page = page, size = size)) {
            is Either.Right -> ok(result.value.toResponse())
            is Either.Left -> internalServerError().body(MessageResponse("Unable to retrieve notifications!"))
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100

        /** Any (page, size) within bounds keeps `page * size` inside Int, so the offset cannot overflow. */
        private const val MAX_PAGE = Int.MAX_VALUE / MAX_PAGE_SIZE
    }
}
