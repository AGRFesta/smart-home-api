package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.domain.Device
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/devices")
class DevicesController {

    @GetMapping
    fun getAllDevices(): Collection<Device> {
        return emptyList()
    }

}
