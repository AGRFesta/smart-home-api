package org.agrfesta.sh.api.controllers

import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.domain.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.TemperatureInterval
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.HeatingAreasService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/heating/areas")
class HeatingAreasController(
    private val heatingAreasService: HeatingAreasService
) {
    private val logger by LoggerDelegate()

    @PostMapping("/{areaId}")
    fun createHeatingSchedule(
        @PathVariable areaId: UUID,
        @RequestBody settings: TemperatureSettings
    ): ResponseEntity<Any> {
        if (settings.temperatureSchedule.hasOverlap()) {
            return status(BAD_REQUEST)
                .body(MessageResponse("A couple of intervals overlaps, this is not allowed!"))
        }
        heatingAreasService.createSetting(AreaTemperatureSetting(
            areaId = areaId,
            defaultTemperature = settings.defaultTemperature,
            temperatureSchedule = settings.temperatureSchedule.toSet()
        )).onLeft {
            return when(it) {
                is PersistenceFailure -> {
                    logger.error("heating settings creation failure", it.exception)
                    status(INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Unable to persist setting for area '$areaId'!"))
                }
                AreaNotFound -> status(NOT_FOUND)
                    .body(MessageResponse("Area with id '$areaId' is missing!"))
            }

        }
        return status(CREATED)
            .body(MessageResponse("Successfully created heating schedule for area with id '$areaId'!"))
    }

}

data class TemperatureSettings(
    val defaultTemperature: Temperature,
    val temperatureSchedule: Collection<TemperatureInterval>
)

fun Collection<TemperatureInterval>.hasOverlap(): Boolean {
    val sortedIntervals = flatMap { interval ->
        if (interval.endTime < interval.startTime && interval.endTime != LocalTime.MIN) {
            listOf(
                TemperatureInterval(interval.temperature, interval.startTime, LocalTime.MAX),
                TemperatureInterval(interval.temperature, LocalTime.MIN, interval.endTime)
            )
        } else {
            listOf(interval)
        }
    }.sortedBy { it.startTime }
    for (i in 1 until sortedIntervals.size) {
        if (sortedIntervals[i].startTime < sortedIntervals[i - 1].endTime) {
            return true
        }
    }
    return false
}


