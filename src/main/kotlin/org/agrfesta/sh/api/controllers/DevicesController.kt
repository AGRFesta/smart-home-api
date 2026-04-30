package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.domain.failures.ExceptionFailure
import org.agrfesta.sh.api.core.domain.failures.MessageFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DevicesController(
    private val devicesProviders: Set<DevicesProvider>,
    private val devicesService: DevicesService
) {
    private val logger by LoggerDelegate()

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Any> {
        logger.error("Devices refresh failure!", e)
        return ResponseEntity<Any>(e.message, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @PostMapping("/refresh")
    fun refresh(): ResponseEntity<Any> {
        val devices = when (val result = devicesService.getAllDto()) {
            is Either.Left -> {
                logger.error("Unable to fetch persisted devices", result.value.exception)
                return internalServerError().body(MessageResponse("Unable to fetch persisted devices!"))
            }
            is Either.Right -> result.value
        }
        val providersDevices: List<ProviderDeviceData> = devicesProviders
            .flatMap { service ->
                service.getAllDevices()
                    .fold(
                        {
                            if (it is MessageFailure) {
                                logger.error("Unable to get providers from ${service.provider}, cause: ${it.message}")
                            } else if (it is ExceptionFailure) {
                                logger.error("Unable to get providers from ${service.provider}", it.exception)
                            } else {
                                logger.error("Unexpected fetch failure from ${service.provider}")
                            }
                            emptyList()
                        },
                        { it }
                    )
            }
        val result = devicesService.refresh(providersDevices, devices)
        result.updatedDevices.forEach { devicesService.update(it) }
        result.detachedDevices.forEach { devicesService.update(it) }
        return ok(DevicesRefreshResponse(
            newDevices = result.newDevices.mapNotNull {
                devicesService.createDevice(it).fold(
                    { failure ->
                        when (failure) {
                            is PersistenceFailure -> logger.error("Unable to persist device", failure.exception)
                            else -> logger.error("Unable to persist device")
                        }
                        null
                    },
                    { uuid -> Device(uuid = uuid, providerData = it).toResponse() }
                )},
            updatedDevices = result.updatedDevices.map { it.toResponse() },
            detachedDevices = result.detachedDevices.map { it.toResponse() }
        ))
    }

}

data class DevicesRefreshResponse(
    val newDevices: Collection<DeviceResponse> = emptyList(),
    val updatedDevices: Collection<DeviceResponse> = emptyList(),
    val detachedDevices: Collection<DeviceResponse> = emptyList()
)
