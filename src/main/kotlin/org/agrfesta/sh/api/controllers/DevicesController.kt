package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.domain.DevicesProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DevicesController(
    private val devicesProviders: Set<DevicesProvider>
) {

    @GetMapping
    fun getAllDevices(): Collection<Device> {
        return devicesProviders.flatMap { it.getAllDevices() }
    }

}
