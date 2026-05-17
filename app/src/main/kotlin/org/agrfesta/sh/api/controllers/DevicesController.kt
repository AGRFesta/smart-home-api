package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.domain.devices.RefreshDevicesResult
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesError
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DevicesController(
    private val refreshDevicesUseCase: RefreshDevicesUseCase
) {
    @PostMapping("/synchronizations")
    fun synchronize(): ResponseEntity<Any> =
        when (val result = refreshDevicesUseCase.execute()) {
            is Either.Left -> when (result.value) {
                RefreshDevicesError -> internalServerError().body(MessageResponse("Device synchronization failed!"))
            }
            is Either.Right -> ok(result.value.toResponse())
        }
}

data class DevicesRefreshResponse(
    val newDevices: Collection<DeviceResponse> = emptyList(),
    val updatedDevices: Collection<DeviceResponse> = emptyList(),
    val detachedDevices: Collection<DeviceResponse> = emptyList()
)

fun RefreshDevicesResult.toResponse() = DevicesRefreshResponse(
    newDevices = newDevices.map { it.toResponse() },
    updatedDevices = updatedDevices.map { it.toResponse() },
    detachedDevices = detachedDevices.map { it.toResponse() }
)
