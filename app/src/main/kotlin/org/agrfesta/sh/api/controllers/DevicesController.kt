package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetDevicesUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.RefreshDevicesUseCase
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.RefreshDevicesResult
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesError
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DevicesController(
    private val refreshDevicesUseCase: RefreshDevicesUseCase,
    private val getDevicesUseCase: GetDevicesUseCase
) {
    @GetMapping
    fun getDevices(
        @RequestParam(required = false) provider: Provider?,
        @RequestParam(required = false) status: DeviceStatus?,
        @RequestParam(required = false) feature: DeviceFeature?
    ): ResponseEntity<Any> =
        when (val result = getDevicesUseCase.execute(provider, status, feature)) {
            is Either.Left -> when (result.value) {
                DeviceRepositoryError -> internalServerError().body(MessageResponse("Unable to retrieve devices!"))
            }
            is Either.Right -> ok(result.value.map { it.toResponse() })
        }

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
