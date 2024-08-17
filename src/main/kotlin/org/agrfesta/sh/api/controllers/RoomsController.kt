package org.agrfesta.sh.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.AssociationConflict
import org.agrfesta.sh.api.persistence.AssociationsDao
import org.agrfesta.sh.api.persistence.DeviceNotFound
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.RoomNameConflict
import org.agrfesta.sh.api.persistence.RoomNotFound
import org.agrfesta.sh.api.persistence.RoomsDao
import org.agrfesta.sh.api.persistence.SameRoomAssociation
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/rooms")
class RoomsController(
    private val randomGenerator: RandomGenerator,
    private val roomsDao: RoomsDao
) {

    @PostMapping
    fun create(@RequestBody request: CreateRoomRequest): ResponseEntity<Any> {
        return when (val result = roomsDao.save(Room(uuid = randomGenerator.uuid(), name = request.name))) {
            is Right -> status(CREATED).body(
                CreatedResourceResponse(
                    message = "Room '${request.name}' successfully created!",
                    resourceId = result.value.uuid.toString() ))
            is Left -> when (result.value) {
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to create Room '${request.name}'!"))
                RoomNameConflict -> badRequest().body(MessageResponse("A Room '${request.name}' already exists!"))
            }
        }
    }

}

data class CreateRoomRequest(val name: String)
