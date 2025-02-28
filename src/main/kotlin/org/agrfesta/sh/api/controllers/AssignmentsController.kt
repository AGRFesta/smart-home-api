package org.agrfesta.sh.api.controllers

import java.util.*
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.NotASensor
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/assignments")
class AssignmentsController(
    private val sensorsAssignmentsDao: SensorsAssignmentsDao
) {

    @PostMapping("/sensors")
    fun assignSensorToArea(@RequestBody request: CreateAssignmentRequest): ResponseEntity<Any> {
        sensorsAssignmentsDao.assign(request.areaId, request.deviceId)
            .mapLeft { when(it) {
                DeviceNotFound -> return status(NOT_FOUND)
                    .body(MessageResponse("Device with id '${request.deviceId}' is missing!"))
                is PersistenceFailure -> return status(INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Unable to assign device ${request.deviceId} to area ${request.areaId}! [${it.exception.message}]"))
                AreaNotFound -> return status(NOT_FOUND)
                    .body(MessageResponse("Area with id '${request.areaId}' is missing!"))
                SensorAlreadyAssigned -> return badRequest()
                    .body(MessageResponse("Device with id '${request.deviceId}' is already assigned to another area!"))
                SameAreaAssignment -> return badRequest()
                    .body(MessageResponse("Device with id '${request.deviceId}' is already assigned to area with id '${request.areaId}'!"))
                NotASensor -> return badRequest()
                    .body(MessageResponse("Device with id '${request.deviceId}' is not a sensor!"))
            } }
        return status(CREATED)
            .body(MessageResponse("Device with id '${request.deviceId}' successfully assigned to area with id '${request.areaId}'!"))
    }

}

data class CreateAssignmentRequest(val areaId: UUID, val deviceId: UUID)
