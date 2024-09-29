package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DevicesProvider
import org.agrfesta.sh.api.domain.devices.DevicesRefreshResult
import org.agrfesta.sh.api.domain.devices.DevicesService
import org.agrfesta.sh.api.persistence.DevicesDao
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DevicesController(
    private val devicesProviders: Set<DevicesProvider>,
    private val devicesService: DevicesService,
    private val devicesDao: DevicesDao
) {

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception) = ResponseEntity<Any>(e.message, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR)

    @PostMapping("/refresh")
    fun refresh(): DevicesRefreshResponse {
        val devices = devicesDao.getAll()
        val providersDevices = devicesProviders
            .flatMap {
                try {
                    it.getAllDevices()
                } catch (e: Exception) {
                    // TODO logs
                    emptyList()
                }
            }
        val result = devicesService.refresh(providersDevices, devices)
        result.updatedDevices.forEach { devicesDao.update(it) }
        result.detachedDevices.forEach { devicesDao.update(it) }
        return DevicesRefreshResponse(
            newDevices = result.newDevices.map { Device(uuid = devicesDao.create(it), dataValue = it) },
            updatedDevices = result.updatedDevices,
            detachedDevices = result.detachedDevices
        )
    }

}

data class DevicesRefreshResponse(
    val newDevices: Collection<Device> = emptyList(),
    val updatedDevices: Collection<Device> = emptyList(),
    val detachedDevices: Collection<Device> = emptyList()
)
