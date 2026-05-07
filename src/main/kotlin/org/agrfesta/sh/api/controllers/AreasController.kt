package org.agrfesta.sh.api.controllers

import arrow.core.Either.Left
import arrow.core.Either.Right
import org.agrfesta.sh.api.core.application.ports.inbounds.CreateAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAreaByIdUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAreasUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.UpdateAreaUseCase
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.noContent
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/areas")
class AreasController(
    private val createAreaUseCase: CreateAreaUseCase,
    private val getAreasUseCase: GetAreasUseCase,
    private val getAreaByIdUseCase: GetAreaByIdUseCase,
    private val deleteAreaUseCase: DeleteAreaUseCase,
    private val updateAreaUseCase: UpdateAreaUseCase
) {

    @PostMapping
    fun create(@RequestBody request: CreateAreaRequest): ResponseEntity<Any> =
        when (val result = createAreaUseCase.execute(name = request.name, isIndoor = request.isIndoor)) {
            is Right -> status(CREATED).body(
                CreatedResourceResponse(
                    message = "Area '${request.name}' successfully created!",
                    resourceId = result.value.uuid.toString() ))
            is Left -> when (result.value) {
                AreaNameConflict -> badRequest().body(MessageResponse("An Area '${request.name}' already exists!"))
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to create Area '${request.name}'!"))
            }
        }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<Any> =
        when (val result = getAreaByIdUseCase.execute(id)) {
            is Right -> ok(result.value)
            is Left -> when (result.value) {
                is AreaNotFound -> ResponseEntity.notFound().build()
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to retrieve area '$id'!"))
            }
        }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UpdateAreaRequest): ResponseEntity<Any> =
        when (val result = updateAreaUseCase.execute(id, request.name, request.isIndoor)) {
            is Right -> ok(result.value)
            is Left -> when (result.value) {
                is AreaNotFound -> ResponseEntity.notFound().build()
                AreaNameConflict -> badRequest()
                    .body(MessageResponse("An Area '${request.name}' already exists!"))
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to update area '$id'!"))
            }
        }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Any> =
        when (val result = deleteAreaUseCase.execute(id)) {
            is Right -> noContent().build()
            is Left -> when (result.value) {
                is AreaNotFound -> ResponseEntity.notFound().build()
                is PersistenceFailure -> internalServerError()
                    .body(MessageResponse("Unable to delete area '$id'!"))
            }
        }

    @GetMapping
    fun getAll(): ResponseEntity<Any> =
        when (val result = getAreasUseCase.execute()) {
            is Right -> ok(result.value)
            is Left -> internalServerError()
                .body(MessageResponse("Unable to retrieve areas!"))
        }

}

data class CreateAreaRequest(val name: String, val isIndoor: Boolean?)
data class UpdateAreaRequest(val name: String, val isIndoor: Boolean)
