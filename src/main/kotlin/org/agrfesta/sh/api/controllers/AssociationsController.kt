package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.persistence.AssociationConflict
import org.agrfesta.sh.api.persistence.AssociationsDao
import org.agrfesta.sh.api.persistence.DeviceNotFound
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.RoomNotFound
import org.agrfesta.sh.api.persistence.SameRoomAssociation
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
import java.util.*

@RestController
@RequestMapping("/associations")
class AssociationsController(
    private val associationsDao: AssociationsDao
) {

    @PostMapping
    fun create(@RequestBody request: CreateAssociationRequest): ResponseEntity<Any> {
        associationsDao.associate(request.roomId, request.deviceId)
            .mapLeft { when(it) {
                DeviceNotFound -> return status(NOT_FOUND)
                    .body(MessageResponse("Device with id '${request.deviceId}' is missing!"))
                is PersistenceFailure -> return status(INTERNAL_SERVER_ERROR)
                    .body(MessageResponse("Unable to associate device ${request.deviceId} to room ${request.roomId}! [${it.exception.message}]"))
                RoomNotFound -> return status(NOT_FOUND)
                    .body(MessageResponse("Room with id '${request.roomId}' is missing!"))
                AssociationConflict -> return badRequest()
                    .body(MessageResponse("Device with id '${request.deviceId}' is already assigned to another room!"))
                SameRoomAssociation -> return badRequest()
                    .body(MessageResponse("Device with id '${request.deviceId}' is already assigned to room with id '${request.roomId}'!"))
            } }
        return status(CREATED)
            .body(MessageResponse("Device with id '${request.deviceId}' successfully assigned to room with id '${request.roomId}'!"))
    }

}

data class CreateAssociationRequest(val roomId: UUID, val deviceId: UUID)
