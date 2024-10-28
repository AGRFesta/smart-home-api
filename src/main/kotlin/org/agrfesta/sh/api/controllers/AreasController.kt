package org.agrfesta.sh.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreaNameConflict
import org.agrfesta.sh.api.persistence.AreaDao
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
@RequestMapping("/areas")
class AreasController(
    private val randomGenerator: RandomGenerator,
    private val areasDao: AreaDao
) {

    @PostMapping
    fun create(@RequestBody request: CreateAreaRequest): ResponseEntity<Any> =
        when (val result = areasDao.save(Area(
            uuid = randomGenerator.uuid(),
            name = request.name,
            isIndoor = request.isIndoor ?: true
        ))) {
            is Right -> status(CREATED).body(
                CreatedResourceResponse(
                    message = "Area '${request.name}' successfully created!",
                    resourceId = result.value.uuid.toString() ))
            is Left -> when (result.value) {
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to create Area '${request.name}'!"))
                AreaNameConflict -> badRequest().body(MessageResponse("An Area '${request.name}' already exists!"))
            }
        }

}

data class CreateAreaRequest(val name: String, val isIndoor: Boolean?)
