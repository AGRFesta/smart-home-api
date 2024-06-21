package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.domain.DevicesProvider
import org.agrfesta.sh.api.domain.DevicesRefreshResult
import org.agrfesta.sh.api.domain.DevicesService
import org.agrfesta.sh.api.persistence.DevicesDao
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException


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
    fun refresh(): DevicesRefreshResult {
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
        result.newDevices.forEach { devicesDao.save(it) }
        result.updatedDevices.forEach { devicesDao.update(it) }
        result.detachedDevices.forEach { devicesDao.update(it) }
        return result
    }

}
