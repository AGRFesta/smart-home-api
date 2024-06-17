package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.domain.DevicesProvider
import org.agrfesta.sh.api.domain.DevicesRefreshResult
import org.agrfesta.sh.api.domain.DevicesService
import org.agrfesta.sh.api.persistence.DevicesDao
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

    @PostMapping("/refresh")
    fun refresh(): DevicesRefreshResult {
        val providersDevices = devicesProviders.flatMap { it.getAllDevices() }
        val devices = devicesDao.getAll()

        val result = devicesService.refresh(providersDevices, devices)

        //TODO persist new devices
        result.newDevices.forEach { devicesDao.save(it) }

        //TODO update persisted devices
        result.updatedDevices.forEach { devicesDao.update(it) }

        //TODO update detached devices

        return result
    }

}
