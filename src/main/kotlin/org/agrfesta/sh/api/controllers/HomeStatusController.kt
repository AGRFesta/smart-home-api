package org.agrfesta.sh.api.controllers

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.commons.average
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.utils.SmartCache
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/home/status")
class HomeStatusController(
    private val areasService: AreasService,
    private val cache: SmartCache
) {

    @GetMapping
    fun getHomeStatus(): ResponseEntity<Any> {
        val areas = try {
            areasService.getAllAreasWithDevices()
        } catch (e: Exception) {
            return status(INTERNAL_SERVER_ERROR)
                .body(MessageResponse("Unable to fetch areas!"))
        }
        val view = areas.map { area ->
                val tempAverage = area.devices
                    .filter { it.isSensor() }
                    .mapNotNull {
                        when(val result = cache.getThermoHygroOf(it)) {
                            is Either.Left -> null
                            is Either.Right -> result.value.temperature
                        }
                    }
                    .average()
                AreaStatusView(area.uuid, area.name, tempAverage)
            }
        return ok(view)
    }

}

data class AreaStatusView(
    val id: UUID,
    val name: String,
    val temperature: Temperature? = null
)
