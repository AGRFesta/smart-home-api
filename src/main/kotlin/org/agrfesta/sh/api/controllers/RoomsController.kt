package org.agrfesta.sh.api.controllers

import arrow.core.Either.Right
import arrow.core.Either.Left
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.RoomNameConflict
import org.agrfesta.sh.api.persistence.RoomPersistenceFailure
import org.agrfesta.sh.api.persistence.RoomsDao
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rooms")
class RoomsController(
    private val randomGenerator: RandomGenerator,
    private val roomsDao: RoomsDao
) {

    @PostMapping
    fun create(
       @RequestBody request: CreateRoomRequest
    ): ResponseEntity<Any> {
        return when (val result = roomsDao.save(Room(uuid = randomGenerator.uuid(), name = request.name))) {
            is Right -> status(CREATED).body(
                CreatedResourceResponse(
                    message = "Room '${request.name}' successfully created!",
                    resourceId = result.value.uuid.toString() ))
            is Left -> when (result.value) {
                is RoomPersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to create Room '${request.name}'!"))
                RoomNameConflict -> badRequest().body(MessageResponse("A Room '${request.name}' already exists!"))
            }
        }
    }

}

data class CreateRoomRequest(val name: String)
