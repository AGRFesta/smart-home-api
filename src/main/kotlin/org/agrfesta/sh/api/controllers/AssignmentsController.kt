package org.agrfesta.sh.api.controllers

import java.util.*
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.NotASensor
import org.agrfesta.sh.api.domain.failures.NotAnActuator
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
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
    private val sensorsAssignmentsDao: SensorsAssignmentsDao,
    private val actuatorsAssignmentsDao: ActuatorsAssignmentsDao,
) {

    @PostMapping("/sensors")
    fun assignSensorToArea(@RequestBody request: CreateAssignmentRequest): ResponseEntity<Any> {
        sensorsAssignmentsDao.assign(request.areaId, request.deviceId).onLeft {
            return it.mapErrorByRequest(request)
        }
        return status(CREATED).body(successfulAssignmentMessage(deviceId = request.deviceId, areaId = request.areaId))
    }

    @PostMapping("/actuators")
    fun assignActuatorToArea(@RequestBody request: CreateAssignmentRequest): ResponseEntity<Any> {
        actuatorsAssignmentsDao.assign(request.areaId, request.deviceId).onLeft {
            return it.mapErrorByRequest(request)
        }
        return status(CREATED).body(successfulAssignmentMessage(deviceId = request.deviceId, areaId = request.areaId))
    }

    private fun SensorAssignmentFailure.mapErrorByRequest(request: CreateAssignmentRequest): ResponseEntity<Any> =
        when(this) {
            DeviceNotFound -> status(NOT_FOUND)
                .body(MessageResponse("Device with id '${request.deviceId}' is missing!"))
            is PersistenceFailure -> status(INTERNAL_SERVER_ERROR)
                .body(unableToAssignDeviceMessage(request.deviceId, request.areaId, exception.message))
            AreaNotFound -> status(NOT_FOUND)
                .body(MessageResponse("Area with id '${request.areaId}' is missing!"))
            NotASensor -> badRequest()
                .body(MessageResponse("Device with id '${request.deviceId}' is not a sensor!"))
            SameAreaAssignment -> badRequest()
                .body(alreadyAssignedMessage(request.deviceId, request.areaId))
            SensorAlreadyAssigned -> badRequest()
                .body(MessageResponse("Device with id '${request.deviceId}' is already assigned to another area!"))
        }

    private fun ActuatorAssignmentFailure.mapErrorByRequest(request: CreateAssignmentRequest): ResponseEntity<Any> =
        when(this) {
            DeviceNotFound -> status(NOT_FOUND)
                .body(MessageResponse("Device with id '${request.deviceId}' is missing!"))
            is PersistenceFailure -> status(INTERNAL_SERVER_ERROR)
                .body(unableToAssignDeviceMessage(request.deviceId, request.areaId, exception.message))
            AreaNotFound -> status(NOT_FOUND)
                .body(MessageResponse("Area with id '${request.areaId}' is missing!"))
            NotAnActuator -> badRequest()
                .body(MessageResponse("Device with id '${request.deviceId}' is not an actuator!"))
            SameAreaAssignment -> badRequest()
                .body(alreadyAssignedMessage(request.deviceId, request.areaId))
        }

    private fun successfulAssignmentMessage(deviceId: UUID, areaId: UUID) =
        MessageResponse("Device with id '$deviceId' successfully assigned to area with id '$areaId'!")

    private fun unableToAssignDeviceMessage(deviceId: UUID, areaId: UUID, cause: String?) =
        MessageResponse("Unable to assign device $deviceId to area $areaId! [${cause?:"no cause"}]")

    private fun alreadyAssignedMessage(deviceId: UUID, areaId: UUID) =
        MessageResponse("Device with id '$deviceId' is already assigned to area with id '$areaId'!")

}

data class CreateAssignmentRequest(val areaId: UUID, val deviceId: UUID)
