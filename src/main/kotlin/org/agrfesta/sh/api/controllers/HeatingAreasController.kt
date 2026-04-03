package org.agrfesta.sh.api.controllers

import java.util.*
import org.agrfesta.sh.api.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.heating.HeatingAreasService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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
        heatingAreasService.createSetting(
            AreaTemperatureSetting(
                areaId = areaId,
                defaultTemperature = settings.defaultTemperature,
                temperatureSchedule = settings.temperatureSchedule.toSet()
            )
        ).onLeft {
            return when (it) {
                OverlappingIntervals -> status(BAD_REQUEST)
                    .body(MessageResponse("A couple of intervals overlaps, this is not allowed!"))
                is AreaNotFound -> status(NOT_FOUND)
                    .body(MessageResponse("Area with id '$areaId' is missing!"))
                is PersistenceFailure -> {
                    logger.error("heating settings creation failure", it.exception)
                    status(INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Unable to persist setting for area '$areaId'!"))
                }
            }
        }
        return status(CREATED)
            .body(MessageResponse("Successfully created heating schedule for area with id '$areaId'!"))
    }

    @GetMapping("/{areaId}")
    fun getHeatingSchedule(@PathVariable areaId: UUID): ResponseEntity<Any> {
        return heatingAreasService.findAreaSetting(areaId).fold(
            { failure ->
                when(failure) {
                    is PersistenceFailure -> {
                        logger.error("heating settings retrieval failure", failure.exception)
                        status(INTERNAL_SERVER_ERROR)
                            .body(MessageResponse("Unable to retrieve setting for area '$areaId'!"))
                    }
                    is AreaNotFound -> status(NOT_FOUND)
                        .body(MessageResponse("Area with id '$areaId' is missing!"))
                }
            },
            { areaSetting ->
                areaSetting?.let {
                    status(OK).body(it.toDto())
                } ?: status(NO_CONTENT).build()
            }
        )
    }

    @DeleteMapping("/{areaId}")
    fun deleteHeatingSchedule(@PathVariable areaId: UUID): ResponseEntity<Any> {
        heatingAreasService.deleteSetting(areaId).onLeft {
            return when(it) {
                is PersistenceFailure -> {
                    logger.error("heating settings deletion failure", it.exception)
                    status(INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Unable to delete setting for area '$areaId'!"))
                }
                is AreaNotFound -> status(NOT_FOUND)
                    .body(MessageResponse("Area with id '$areaId' is missing!"))
            }
        }
        return status(OK)
            .body(MessageResponse("Successfully deleted heating schedule for area with id '$areaId'!"))
    }

}

private fun AreaTemperatureSetting.toDto() = TemperatureSettings(
    defaultTemperature = defaultTemperature,
    temperatureSchedule = temperatureSchedule
)

data class TemperatureSettings(
    val defaultTemperature: Temperature,
    val temperatureSchedule: Collection<TemperatureInterval>
)
