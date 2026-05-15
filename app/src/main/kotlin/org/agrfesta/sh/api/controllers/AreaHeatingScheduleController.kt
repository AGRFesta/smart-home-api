package org.agrfesta.sh.api.controllers

import java.time.LocalTime
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHeatingScheduleUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.ReplaceHeatingScheduleUseCase
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.OverlappingIntervals
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/areas")
class AreaHeatingScheduleController(
    private val deleteHeatingScheduleUseCase: DeleteHeatingScheduleUseCase,
    private val replaceHeatingScheduleUseCase: ReplaceHeatingScheduleUseCase,
    private val getHeatingScheduleUseCase: GetHeatingScheduleUseCase
) {
    companion object {
        val DEFAULT_TEMPERATURE: Temperature = Temperature.of("20.0")
    }

    @GetMapping("/{areaId}/heating-schedule")
    fun getHeatingSchedule(@PathVariable areaId: UUID): ResponseEntity<Any> =
        getHeatingScheduleUseCase.execute(areaId).fold(
            { failure ->
                when (failure) {
                    is AreaNotFound -> status(NOT_FOUND)
                        .body(MessageResponse("Area with id '$areaId' is missing!"))
                    HeatingScheduleRepositoryError -> status(INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Internal server error while retrieving heating schedule."))
                }
            },
            { schedule ->
                val response = schedule?.toResponse() ?: HeatingScheduleResponse(
                    defaultTemperature = DEFAULT_TEMPERATURE,
                    intervals = emptyList()
                )
                status(OK).body(response)
            }
        )

    @DeleteMapping("/{areaId}/heating-schedule")
    fun deleteHeatingSchedule(@PathVariable areaId: UUID): ResponseEntity<Any> =
        deleteHeatingScheduleUseCase.execute(areaId).fold(
            { failure ->
                when (failure) {
                    is AreaNotFound -> status(NOT_FOUND)
                        .body(MessageResponse("Area with id '$areaId' is missing!"))
                    HeatingScheduleRepositoryError -> status(INTERNAL_SERVER_ERROR)
                        .body(MessageResponse("Internal server error while deleting heating schedule."))
                }
            },
            { status(NO_CONTENT).build() }
        )

    @PutMapping("/{areaId}/heating-schedule")
    fun replaceHeatingSchedule(
        @PathVariable areaId: UUID,
        @RequestBody request: HeatingScheduleRequest
    ): ResponseEntity<Any> = replaceHeatingScheduleUseCase.execute(
        areaId = areaId,
        defaultTemperature = request.defaultTemperature,
        intervals = request.intervals.map { TemperatureInterval(it.temperature, it.startTime, it.endTime) }
    ).fold(
        { failure ->
            when (failure) {
                OverlappingIntervals -> {
                    val problem = ProblemDetail.forStatusAndDetail(
                        UNPROCESSABLE_ENTITY, "The provided heating intervals overlap."
                    )
                    problem.title = "Overlapping Intervals"
                    status(UNPROCESSABLE_ENTITY).body(problem)
                }
                is AreaNotFound -> status(NOT_FOUND)
                    .body(MessageResponse("Area with id '$areaId' is missing!"))
                HeatingScheduleRepositoryError -> status(INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Internal server error while saving heating schedule."))
            }
        },
        { schedule -> status(OK).body(schedule.toResponse()) }
    )

}

data class HeatingScheduleRequest(
    val defaultTemperature: Temperature,
    val intervals: List<IntervalRequest>
)

data class IntervalRequest(
    val temperature: Temperature,
    val startTime: LocalTime,
    val endTime: LocalTime
)
