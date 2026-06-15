package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAlertsUseCase
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.failures.AlertRepositoryError
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/alerts")
class AlertsController(
    private val getAlertsUseCase: GetAlertsUseCase
) {

    @GetMapping
    fun getAlerts(@RequestParam(required = false) status: AlertStatus?): ResponseEntity<Any> =
        when (val result = getAlertsUseCase.execute(status)) {
            is Either.Right -> ok(result.value.map { it.toResponse() })
            is Either.Left -> when (result.value) {
                AlertRepositoryError -> internalServerError().body(MessageResponse("Unable to retrieve alerts!"))
            }
        }
}
